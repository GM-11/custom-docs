package models

import (
	"encoding/json"
	"fmt"
	"log"
	"strconv"
	"time"

	"custom_docs.com/m/v2/engine"
	"custom_docs.com/m/v2/messages"
	"github.com/google/uuid"
	"github.com/gorilla/websocket"
)

type Hub struct {
	ClientMap       map[string]*Client
	DocumentState   DocumentState
	IncomingChannel chan ChannelData
	Register        chan *Client
	Unregister      chan *Client
	LamportClock    int64
	Done            chan struct{}
}

func coerceOperationData(op *engine.Operation) {
	if op == nil {
		return
	}

	if op.Type == engine.INSERT {
		switch v := op.Data.(type) {
		case string:
			// already correct
		case []byte:
			op.Data = string(v)
		case float64:

			op.Data = fmt.Sprintf("%v", v)
		case int:
			op.Data = fmt.Sprintf("%d", v)
		default:

			op.Data = fmt.Sprintf("%v", v)
		}
	} else { // DELETE
		switch v := op.Data.(type) {
		case int:

		case float64:
			op.Data = int(v)
		case string:

			if n, err := strconv.Atoi(v); err == nil {
				op.Data = n
			} else {
				op.Data = 0
			}
		default:

			op.Data = 0
		}
	}
}

func (h *Hub) Run(kafkaProducer *messages.KafkaProducer) {
	for {
		select {
		case client := <-h.Register: // new connection
			log.Printf(
				"hub register: clientId=%q (before) clients=%d",
				client.ClientId,
				len(h.ClientMap),
			)

			// Enforce one active websocket per (hub, clientId).
			// In dev (React StrictMode / HMR), the browser often opens 2 sockets; one gets dropped,
			// causing broken pipes/unexpected EOF and preventing stable collaboration.
			// Strategy: close & replace any existing connection for the same clientId.
			if existing, ok := h.ClientMap[client.ClientId]; ok && existing != nil && existing.WsConn != nil {
				log.Printf("hub register: replacing existing connection for clientId=%q", client.ClientId)

				// Best-effort close of the old websocket; ignore errors.
				_ = existing.WsConn.WriteMessage(
					websocket.CloseMessage,
					websocket.FormatCloseMessage(websocket.CloseNormalClosure, "replaced by new connection"),
				)
				_ = existing.WsConn.Close()

				// Close the old outbound channel if it's still open.
				// (WritePump will exit once the channel closes.)
				func() {
					defer func() { recover() }()
					close(existing.OutboundChannel)
				}()
			}

			h.ClientMap[client.ClientId] = client

			log.Printf(
				"hub register: clientId=%q (after) clients=%d",
				client.ClientId,
				len(h.ClientMap),
			)
		case client := <-h.Unregister: // disconnection
			log.Printf(
				"hub unregister: clientId=%q (before) clients=%d",
				client.ClientId,
				len(h.ClientMap),
			)

			delete(h.ClientMap, client.ClientId)

			log.Printf(
				"hub unregister: clientId=%q (after) clients=%d",
				client.ClientId,
				len(h.ClientMap),
			)

			if len(h.ClientMap) == 0 {
				close(h.Done)
				return
			}

		case channelData := <-h.IncomingChannel: // operation on the document
			incomingOperation := channelData.Payload.(OperationPayload).Operation

			log.Printf(
				"hub incoming: senderClientId=%q incomingOp={type=%d pos=%d ver=%d data=%v} docVer(before)=%d opsLen=%d",
				channelData.ClientId,
				incomingOperation.Type,
				incomingOperation.Position,
				incomingOperation.Version,
				incomingOperation.Data,
				h.DocumentState.Version,
				len(h.DocumentState.Operations),
			)

			for _, op := range h.DocumentState.Operations[channelData.Version:] {
				incomingOperation = engine.PerformTransformation(incomingOperation, op)
			}

			log.Printf(
				"hub transformed: senderClientId=%q transformedOp={type=%d pos=%d ver=%d data=%v}",
				channelData.ClientId,
				incomingOperation.Type,
				incomingOperation.Position,
				incomingOperation.Version,
				incomingOperation.Data,
			)

			coerceOperationData(&incomingOperation)

			docLen := len(h.DocumentState.Content)

			if incomingOperation.Position < 0 {
				log.Printf("sanitizing: incoming position %d < 0, clamping to 0", incomingOperation.Position)
				incomingOperation.Position = 0
			}
			if incomingOperation.Position > docLen {
				log.Printf("sanitizing: incoming position %d > docLen(%d), clamping to %d", incomingOperation.Position, docLen, docLen)
				incomingOperation.Position = docLen
			}

			if incomingOperation.Type == engine.DELETE {
				var lengthInt int
				switch v := incomingOperation.Data.(type) {
				case int:
					lengthInt = v
				case float64:
					lengthInt = int(v)
				case string:
					if n, err := strconv.Atoi(v); err == nil {
						lengthInt = n
					} else {
						lengthInt = 0
					}
				default:
					lengthInt = 0
				}

				if lengthInt < 0 {
					log.Printf("sanitizing: delete length %d < 0, clamping to 0", lengthInt)
					lengthInt = 0
				}

				if incomingOperation.Position >= docLen {
					lengthInt = 0
				} else if incomingOperation.Position+lengthInt > docLen {
					log.Printf("sanitizing: delete extends past end (%d + %d > %d), clamping length to %d", incomingOperation.Position, lengthInt, docLen, docLen-incomingOperation.Position)
					lengthInt = docLen - incomingOperation.Position
				}

				incomingOperation.Data = lengthInt
			}

			nextContent, err := engine.ApplyOperationSafe(h.DocumentState.Content, incomingOperation)
			if err != nil {
				log.Printf(
					"hub apply skipped: senderClientId=%q err=%v op={type=%d pos=%d ver=%d data=%v}",
					channelData.ClientId,
					err,
					incomingOperation.Type,
					incomingOperation.Position,
					incomingOperation.Version,
					incomingOperation.Data,
				)
				continue
			}
			h.DocumentState.Content = nextContent
			h.DocumentState.Version += 1

			log.Printf(
				"hub applied: senderClientId=%q docVer(after)=%d contentLen=%d",
				channelData.ClientId,
				h.DocumentState.Version,
				len(h.DocumentState.Content),
			)

			for _, client := range h.ClientMap {
				log.Printf(
					"fan-out check: receiverClientId=%q senderClientId=%q match=%v",
					client.ClientId,
					channelData.ClientId,
					client.ClientId == channelData.ClientId,
				)

				out := ChannelData{
					Payload:  MessagePayload(OperationPayload{Operation: incomingOperation}),
					ClientId: channelData.ClientId,
					Version:  h.DocumentState.Version,
				}

				log.Printf(
					"hub broadcast: to=%q envelopeClientId(sender)=%q version=%d op={type=%d pos=%d ver=%d data=%v}",
					client.ClientId,
					out.ClientId,
					out.Version,
					incomingOperation.Type,
					incomingOperation.Position,
					incomingOperation.Version,
					incomingOperation.Data,
				)

				client.OutboundChannel <- out
			}

			h.DocumentState.Operations = append(h.DocumentState.Operations, incomingOperation)

			var operationData string
			if incomingOperation.Type == engine.INSERT {
				var text string
				switch v := incomingOperation.Data.(type) {
				case string:
					text = v
				case []byte:
					text = string(v)
				case float64:
					text = fmt.Sprintf("%v", v)
				case int:
					text = fmt.Sprintf("%d", v)
				default:
					text = fmt.Sprintf("%v", v)
				}
				m := map[string]any{
					"text":     text,
					"type":     "insert",
					"position": incomingOperation.Position,
				}
				jsonBytes, _ := json.Marshal(m)
				operationData = string(jsonBytes)
			} else {
				// delete length should be an int
				var lengthInt int
				switch v := incomingOperation.Data.(type) {
				case int:
					lengthInt = v
				case float64:
					lengthInt = int(v)
				case string:
					if n, err := strconv.Atoi(v); err == nil {
						lengthInt = n
					} else {
						lengthInt = 0
					}
				default:
					lengthInt = 0
				}
				m := map[string]any{
					"length":   lengthInt,
					"type":     "delete",
					"position": incomingOperation.Position,
				}
				jsonBytes, _ := json.Marshal(m)
				operationData = string(jsonBytes)
			}

			operationId := uuid.NewString()
			opMsg := messages.OperationMessage{
				Type:          "operation",
				OperationId:   operationId,
				DocumentId:    h.DocumentState.Id,
				UserId:        channelData.ClientId,
				LamportClock:  h.LamportClock,
				OperationData: operationData,
				Timestamp:     time.Now().Unix(),
			}
			clockSnapshot := h.LamportClock
			shouldSnapshot := clockSnapshot%100 == 0
			go func() {
				kafkaProducer.PublishOperation(opMsg)

				if shouldSnapshot {
					fmt.Println("Publishing Snapshot message")
					snapMsg := messages.SnapshotMessage{
						Type:           "snapshot",
						DocumentId:     h.DocumentState.Id,
						DocumentString: h.DocumentState.Content,
						LamportClock:   h.LamportClock,
						Timestamp:      time.Now().Unix(),
					}
					kafkaProducer.PublishSnapshot(snapMsg)
				}
			}()

			h.LamportClock += 1

		}
	}

}

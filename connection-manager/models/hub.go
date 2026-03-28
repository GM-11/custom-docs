package models

import (
	"encoding/json"
	"time"

	"custom_docs.com/m/v2/engine"
	"custom_docs.com/m/v2/messages"
	"github.com/google/uuid"
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

func (h *Hub) Run(kafkaProducer *messages.KafkaProducer) {
	for {
		select {
		case client := <-h.Register: // new connection
			h.ClientMap[client.ClientId] = client
		case client := <-h.Unregister: // disconnection
			delete(h.ClientMap, client.ClientId)

			if len(h.ClientMap) == 0 {
				close(h.Done)
				return
			}

		case channelData := <-h.IncomingChannel: // operation on the document
			incomingOperation := channelData.Payload.(OperationPayload).Operation
			for _, op := range h.DocumentState.Operations[channelData.Version:] {
				incomingOperation = engine.PerformTransformation(incomingOperation, op)
			}

			h.DocumentState.Content = engine.ApplyOperation(h.DocumentState.Content, incomingOperation)

			h.DocumentState.Version += 1

			for _, client := range h.ClientMap {
				if client.ClientId != channelData.ClientId {
					client.OutboundChannel <- ChannelData{
						Payload:  MessagePayload(OperationPayload{Operation: incomingOperation}),
						ClientId: client.ClientId,
						Version:  h.DocumentState.Version,
					}
				}
			}

			h.DocumentState.Operations = append(h.DocumentState.Operations, incomingOperation)
			h.LamportClock += 1

			var operationData string
			if incomingOperation.Type == engine.INSERT {
				m := map[string]any{
					"text":     incomingOperation.Data.(string),
					"type":     "insert",
					"position": incomingOperation.Position,
				}
				jsonBytes, _ := json.Marshal(m)
				operationData = string(jsonBytes)
			} else {
				m := map[string]any{
					"length":   incomingOperation.Data.(int),
					"type":     "delete",
					"position": incomingOperation.Position,
				}
				jsonBytes, _ := json.Marshal(m)
				operationData = string(jsonBytes)
			}

			opMsg := messages.OperationMessage{
				Type:          "operation",
				OperationId:   uuid.NewString(),
				DocumentId:    h.DocumentState.Id,
				UserId:        channelData.ClientId,
				LamportClock:  h.LamportClock,
				OperationData: operationData,
				Timestamp:     time.Now().Unix(),
			}

			go func() {
				kafkaProducer.PublishOperation(opMsg)
			}()
			if h.LamportClock%100 == 0 {
				snapMsg := messages.SnapshotMessage{
					Type:           "snapshot",
					DocumentId:     h.DocumentState.Id,
					DocumentString: h.DocumentState.Content,
					LamportClock:   h.LamportClock,
					Timestamp:      time.Now().Unix(),
					OperationId:    uuid.NewString(),
				}
				go func() {
					kafkaProducer.PublishSnapshot(snapMsg)
				}()
			}
		}
	}

}

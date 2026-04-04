package models

import (
	"encoding/json"
	"log"

	"custom_docs.com/m/v2/engine"
	"github.com/gorilla/websocket"
)

type Client struct {
	ClientId        string
	OutboundChannel chan ChannelData
	Hub             *Hub
	WsConn          *websocket.Conn
}

func safeCloseOutbound(ch chan ChannelData) {
	if ch == nil {
		return
	}
	defer func() {
		recover()
	}()
	close(ch)
}

func (c *Client) ReadPump() {
	for {
		var operation engine.Operation

		_, operationBytes, err := c.WsConn.ReadMessage()
		if err != nil {
			log.Printf("WS read error: clientId=%s err=%v", c.ClientId, err)
			c.Hub.Unregister <- c
			safeCloseOutbound(c.OutboundChannel)
			return
		}

		var raw struct {
			Type     int             `json:"type"`
			Position int             `json:"position"`
			Version  int             `json:"version"`
			ClientID string          `json:"clientId"`
			Data     json.RawMessage `json:"data"`
		}

		if err := json.Unmarshal(operationBytes, &raw); err != nil {
			log.Printf(
				"WS invalid op JSON: clientId=%s err=%v raw=%s",
				c.ClientId,
				err,
				string(operationBytes),
			)
			continue
		}

		operation.Type = raw.Type
		operation.Position = raw.Position
		operation.Version = raw.Version
		operation.ClientID = raw.ClientID

		if operation.Type == engine.INSERT {
			var s string
			if err := json.Unmarshal(raw.Data, &s); err != nil {
				var iface interface{}
				if err2 := json.Unmarshal(raw.Data, &iface); err2 == nil {
					if str, ok := iface.(string); ok {
						s = str
					} else {
						s = ""
					}
				} else {
					s = ""
				}
			}
			operation.Data = s
		} else {
			var length int
			if err := json.Unmarshal(raw.Data, &length); err != nil {
				var f float64
				if err2 := json.Unmarshal(raw.Data, &f); err2 == nil {
					length = int(f)
				} else {
					// last-resort attempt to decode to interface and coerce numbers
					var iface interface{}
					if err3 := json.Unmarshal(raw.Data, &iface); err3 == nil {
						switch v := iface.(type) {
						case float64:
							length = int(v)
						case int:
							length = v
						default:
							length = 0
						}
					} else {
						length = 0
					}
				}
			}
			operation.Data = length
		}

		operation.ClientID = c.ClientId

		log.Printf(
			"WS op received: senderClientId=%s type=%d position=%d version=%d data=%v",
			operation.ClientID,
			operation.Type,
			operation.Position,
			operation.Version,
			operation.Data,
		)

		c.Hub.IncomingChannel <- ChannelData{
			Payload:  MessagePayload(OperationPayload{Operation: operation}),
			ClientId: operation.ClientID,
			Version:  operation.Version,
		}
	}
}

func (c *Client) WritePump() {
	for channelData := range c.OutboundChannel {
		channelDataBytes, err := json.Marshal(channelData)
		if err != nil {
			log.Printf("WS marshal outbound error: clientId=%s err=%v", c.ClientId, err)
			continue
		}

		log.Printf("WS outbound to clientId=%s bytes=%s", c.ClientId, string(channelDataBytes))

		if err := c.WsConn.WriteMessage(websocket.TextMessage, channelDataBytes); err != nil {
			log.Printf("WS write error: clientId=%s err=%v", c.ClientId, err)
			c.Hub.Unregister <- c
			safeCloseOutbound(c.OutboundChannel)
			return
		}
	}
}

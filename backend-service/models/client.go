package models

import (
	"encoding/json"

	"custom_docs.com/m/v2/engine"
	"github.com/gorilla/websocket"
)

type Client struct {
	ClientId        string
	OutboundChannel chan ChannelData
	Hub             *Hub
	WsConn          *websocket.Conn
}

func (c *Client) ReadPump() {

	for {
		var operation engine.Operation
		_, operationBytes, err := c.WsConn.ReadMessage()
		json.Unmarshal(operationBytes, &operation)

		if err != nil {
			c.Hub.Unregister <- c
			close(c.OutboundChannel)
			return
		}

		c.Hub.IncomingChannel <- ChannelData{
			Operation: operation,
			ClientId:  operation.ClientID,
			Version:   operation.Version,
		}
	}

}

func (c *Client) WritePump() {
	for channelData := range c.OutboundChannel {
		channelDataBytes, _ := json.Marshal(channelData)
		_ = c.WsConn.WriteMessage(websocket.TextMessage, channelDataBytes)
	}

}

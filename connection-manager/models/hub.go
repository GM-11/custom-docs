package models

import "custom_docs.com/m/v2/engine"

type Hub struct {
	ClientMap       map[string]*Client
	DocumentState   DocumentState
	IncomingChannel chan ChannelData
	Register        chan *Client
	Unregister      chan *Client
}

func (h *Hub) Run() {
	for {
		select {
		case client := <-h.Register: // new connection
			h.ClientMap[client.ClientId] = client
		case client := <-h.Unregister: // disconnection
			delete(h.ClientMap, client.ClientId)
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
		}
	}

}

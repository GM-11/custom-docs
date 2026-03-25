package models

import (
	"fmt"
	"sync"

	"custom_docs.com/m/v2/engine"
	"github.com/google/uuid"
	"github.com/gorilla/websocket"
)

type HubManager struct {
	Hubs map[string]*Hub
	mu   sync.Mutex
}

func (manager *HubManager) CreateNewDocument(clientId string) (string, error) {
	manager.mu.Lock()
	hubId := uuid.New().String()

	// TODO: create this new id in DB

	newHub := Hub{
		IncomingChannel: make(chan ChannelData),
		Register:        make(chan *Client),
		Unregister:      make(chan *Client),
		ClientMap:       make(map[string]*Client),
		DocumentState:   DocumentState{Content: "", Version: 0, Operations: []engine.Operation{}, Id: hubId},
	}

	manager.Hubs[hubId] = &newHub
	manager.mu.Unlock()

	fmt.Println("Client connected!")
	go newHub.Run()

	return hubId, nil
}

func (manager *HubManager) ConnectToDocument(clientId, hubId string, conn *websocket.Conn) {

	manager.mu.Lock()
	hub, exists := manager.Hubs[hubId]
	manager.mu.Unlock()
	// TODO: check if this hubId exists in DB, if not return error to client

	if !exists {
		fmt.Printf("Hub %s does not exist\n", hubId)
		conn.WriteMessage(websocket.CloseMessage, websocket.FormatCloseMessage(websocket.CloseNormalClosure, "document not found"))
		conn.Close()
		return
	}

	fmt.Println("Client connected to existing document!")

	client := Client{
		ClientId:        clientId,
		WsConn:          conn,
		OutboundChannel: make(chan ChannelData),
		Hub:             hub,
	}

	hub.Register <- &client
	go client.WritePump()
	client.ReadPump()

}

func (manager *HubManager) GetDocumentState(hubId string) (DocumentState, error) {
	manager.mu.Lock()
	hub, exists := manager.Hubs[hubId]
	manager.mu.Unlock()

	if !exists {
		return DocumentState{}, fmt.Errorf("document not found")
	}

	return hub.DocumentState, nil
}

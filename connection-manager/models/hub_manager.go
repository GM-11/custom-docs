package models

import (
	"fmt"
	"sync"

	"custom_docs.com/m/v2/engine"
	"custom_docs.com/m/v2/messages"
	"github.com/google/uuid"
	"github.com/gorilla/websocket"
)

type HubManager struct {
	Hubs     map[string]*Hub
	mu       sync.Mutex
	Producer *messages.KafkaProducer
}

func (manager *HubManager) CreateNewDocument(clientId, title string) (string, error) {
	manager.mu.Lock()
	hubId := uuid.New().String()

	// TODO: create this new id in DB
	// manager.PublishCreateDocument()

	manager.Producer.PublishCreateDocument(messages.CreateDocumentMessage{UserId: clientId, Title: title})

	newHub := Hub{
		IncomingChannel: make(chan ChannelData),
		Register:        make(chan *Client),
		Unregister:      make(chan *Client),
		ClientMap:       make(map[string]*Client),
		DocumentState:   DocumentState{Content: "", Version: 0, Operations: []engine.Operation{}, Id: hubId},
		LamportClock:    0,
		Done:            make(chan struct{}),
	}

	manager.Hubs[hubId] = &newHub
	manager.mu.Unlock()

	go func() {
		<-newHub.Done
		manager.RemoveHub(hubId)
	}()

	fmt.Println("Client connected!")
	go manager.Hubs[hubId].Run(manager.Producer)

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
	client.OutboundChannel <- ChannelData{
		Payload: MessagePayload(DocumentState{
			Version:    hub.DocumentState.Version,    // version stored in DB
			Content:    hub.DocumentState.Content,    // version stored in DB
			Operations: hub.DocumentState.Operations, // operations stored in DB
			Id:         hubId,
		}),
	}
	client.ReadPump()
}

func (manager *HubManager) RemoveHub(hubId string) {
	manager.mu.Lock()
	delete(manager.Hubs, hubId)
	manager.mu.Unlock()
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

func (manager *HubManager) LoadExistingDocument(hubId string) error {
	manager.mu.Lock()
	defer manager.mu.Unlock()

	if _, exists := manager.Hubs[hubId]; exists {
		return nil
	}

	docText, lastClock, err := recoverDocumentState(hubId)

	if err != nil {
		return fmt.Errorf("failed to recover document state: %w", err)
	}

	newHub := Hub{
		IncomingChannel: make(chan ChannelData),
		Register:        make(chan *Client),
		Unregister:      make(chan *Client),
		ClientMap:       make(map[string]*Client),
		DocumentState:   DocumentState{Content: docText, Version: 0, Operations: []engine.Operation{}, Id: hubId},
		LamportClock:    lastClock,
		Done:            make(chan struct{}),
	}

	go func() {
		<-newHub.Done
		manager.RemoveHub(hubId)
	}()

	manager.Hubs[hubId] = &newHub
	go manager.Hubs[hubId].Run(manager.Producer)

	return nil
}

func (manager *HubManager) HubExists(hubId string) bool {
	manager.mu.Lock()
	defer manager.mu.Unlock()
	_, exists := manager.Hubs[hubId]
	return exists
}

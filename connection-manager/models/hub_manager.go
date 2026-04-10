package models

import (
	"fmt"
	"sync"

	"custom_docs.com/m/v2/docmanagercomm"
	"custom_docs.com/m/v2/engine"
	"custom_docs.com/m/v2/messages"
	"github.com/gorilla/websocket"
)

type HubManager struct {
	Hubs     map[string]*Hub
	mu       sync.Mutex
	Producer *messages.KafkaProducer

	// recoveryMu guards recoveryLocks map itself
	recoveryMu sync.Mutex
	// recoveryLocks ensures only one goroutine recovers/creates a hub per hubId at a time
	recoveryLocks map[string]*sync.Mutex
}

func (manager *HubManager) CreateNewDocument(clientId, title, authHeader string) (string, error) {
	fmt.Printf("Creating new document with title: %s for clientId: %s\n", title, clientId)
	manager.mu.Lock()

	createdHubId, err := docmanagercomm.CreateDocumentRequest(title, clientId, authHeader)
	if err != nil {
		return "", fmt.Errorf("failed to create document: %w", err)
	}
	newHub := Hub{
		IncomingChannel: make(chan ChannelData, 100),
		Register:        make(chan *Client, 10),
		Unregister:      make(chan *Client, 10),
		ClientMap:       make(map[string]*Client),
		DocumentState:   DocumentState{Content: "", Version: 0, Operations: []engine.Operation{}, Id: createdHubId},
		LamportClock:    0,
		Done:            make(chan struct{}),
	}

	manager.Hubs[createdHubId] = &newHub

	// Ensure recovery lock map is initialized for future recoveries
	if manager.recoveryLocks == nil {
		manager.recoveryLocks = make(map[string]*sync.Mutex)
	}

	manager.mu.Unlock()

	go func() {
		<-newHub.Done
		manager.RemoveHub(createdHubId)
	}()

	fmt.Println("Client connected!")
	go manager.Hubs[createdHubId].Run(manager.Producer)

	return createdHubId, nil
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
		OutboundChannel: make(chan ChannelData, 100),
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
		ClientId: clientId,
		Version:  hub.DocumentState.Version,
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
	// Ensure per-hub recovery lock exists
	manager.recoveryMu.Lock()
	if manager.recoveryLocks == nil {
		manager.recoveryLocks = make(map[string]*sync.Mutex)
	}
	lock, ok := manager.recoveryLocks[hubId]
	if !ok {
		lock = &sync.Mutex{}
		manager.recoveryLocks[hubId] = lock
	}
	manager.recoveryMu.Unlock()

	// Only one goroutine can recover/create a hub for this hubId at a time
	lock.Lock()
	defer lock.Unlock()

	// Double-check existence after acquiring per-hub lock
	manager.mu.Lock()
	if _, exists := manager.Hubs[hubId]; exists {
		manager.mu.Unlock()
		return nil
	}
	manager.mu.Unlock()

	docText, lastClock, err := recoverDocumentState(hubId)
	if err != nil {
		return fmt.Errorf("failed to recover document state: %w", err)
	}

	newHub := Hub{
		IncomingChannel: make(chan ChannelData, 100),
		Register:        make(chan *Client, 10),
		Unregister:      make(chan *Client, 10),
		ClientMap:       make(map[string]*Client),
		DocumentState:   DocumentState{Content: docText, Version: 0, Operations: []engine.Operation{}, Id: hubId},
		LamportClock:    lastClock,
		Done:            make(chan struct{}),
	}

	go func() {
		<-newHub.Done
		manager.RemoveHub(hubId)
	}()

	// Store hub under global manager lock
	manager.mu.Lock()
	// Another goroutine may have inserted while we were recovering; if so don't replace.
	if _, exists := manager.Hubs[hubId]; !exists {
		manager.Hubs[hubId] = &newHub
		go manager.Hubs[hubId].Run(manager.Producer)
	}
	manager.mu.Unlock()

	return nil
}

func (manager *HubManager) HubExists(hubId string) bool {
	manager.mu.Lock()
	defer manager.mu.Unlock()
	_, exists := manager.Hubs[hubId]
	return exists
}

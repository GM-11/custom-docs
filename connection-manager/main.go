package main

import (
	"encoding/json"
	"fmt"
	"log"
	"net/http"

	"custom_docs.com/m/v2/messages"
	"custom_docs.com/m/v2/models"
	"github.com/gorilla/websocket"
	"github.com/segmentio/kafka-go"
)

var upgrader = websocket.Upgrader{

	CheckOrigin: func(r *http.Request) bool {
		return true // Allowing all origins for development
	},
}

func createNewDocumentHandler(manager *models.HubManager) func(w http.ResponseWriter, r *http.Request) {
	return func(w http.ResponseWriter, r *http.Request) {
		clientId := r.URL.Query().Get("clientId")
		if clientId == "" {
			w.WriteHeader(http.StatusBadRequest)
			json.NewEncoder(w).Encode(map[string]string{"error": "clientId is required"})
			return
		}
		// conn, err := upgrader.Upgrade(w, r, nil)

		hubId, err := manager.CreateNewDocument(clientId)
		if err != nil {
			log.Println("Error creating new document:", err)
			w.WriteHeader(http.StatusInternalServerError)
			json.NewEncoder(w).Encode(err)
		}

		json.NewEncoder(w).Encode(hubId)
	}
}

func connectToDocumentHandler(manager *models.HubManager) func(w http.ResponseWriter, r *http.Request) {
	return func(w http.ResponseWriter, r *http.Request) {
		clientId := r.URL.Query().Get("clientId")
		hubId := r.URL.Query().Get("hubId")

		if clientId == "" || hubId == "" {
			w.WriteHeader(http.StatusBadRequest)
			json.NewEncoder(w).Encode(map[string]string{"error": "clientId and hubId are required"})
			return
		}
		conn, err := upgrader.Upgrade(w, r, nil)
		if err != nil {
			log.Println("Upgrade error:", err)
			return
		}
		manager.ConnectToDocument(clientId, hubId, conn)
	}
}

func getDocumentStateHandler(manager *models.HubManager) func(w http.ResponseWriter, r *http.Request) {
	return func(w http.ResponseWriter, r *http.Request) {
		hubId := r.URL.Query().Get("hubId")
		if hubId == "" {
			w.WriteHeader(http.StatusBadRequest)
			json.NewEncoder(w).Encode(map[string]string{"error": "hubId is required"})
			return
		}

		state, err := manager.GetDocumentState(hubId)
		if err != nil {
			w.WriteHeader(http.StatusNotFound)
			json.NewEncoder(w).Encode(map[string]string{"error": err.Error()})
			return
		}
		json.NewEncoder(w).Encode(state)

	}
}
func main() {
	mux := http.NewServeMux()

	kafkaProducer := messages.KafkaProducer{
		Writer: &kafka.Writer{
			Addr:     kafka.TCP("localhost:9092"),
			Topic:    "document-ops",
			Balancer: &kafka.LeastBytes{},
		},
	}
	defer kafkaProducer.Close()
	manager := models.HubManager{
		Hubs:     make(map[string]*models.Hub),
		Producer: &kafkaProducer}
	mux.HandleFunc("POST /documents", createNewDocumentHandler(&manager))
	mux.HandleFunc("GET /ws", connectToDocumentHandler(&manager))
	mux.HandleFunc("GET /document", getDocumentStateHandler(&manager))

	fmt.Println("Server started on :8080")
	log.Fatal(http.ListenAndServe(":8080", mux))
}

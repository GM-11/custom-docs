package handlers

import (
	"encoding/json"
	"log"
	"net/http"

	"custom_docs.com/m/v2/auth"
	"custom_docs.com/m/v2/docmanagercomm"
	"custom_docs.com/m/v2/models"
	"github.com/gorilla/websocket"
)

var upgrader = websocket.Upgrader{

	CheckOrigin: func(r *http.Request) bool {
		return true // Allowing all origins for development
	},
}

func CreateNewDocumentHandler(manager *models.HubManager) func(w http.ResponseWriter, r *http.Request) {
	return func(w http.ResponseWriter, r *http.Request) {
		clientId := r.Context().Value(auth.UserIdKey).(string)
		title := r.URL.Query().Get("title")
		if clientId == "" {
			w.WriteHeader(http.StatusBadRequest)
			json.NewEncoder(w).Encode(map[string]string{"error": "clientId is required"})
			return
		}
		hubId, err := manager.CreateNewDocument(clientId, title)
		if err != nil {
			log.Println("Error creating new document:", err)
			w.WriteHeader(http.StatusInternalServerError)
			json.NewEncoder(w).Encode(err)
		}

		json.NewEncoder(w).Encode(hubId)
	}
}

func ConnectToDocumentHandler(manager *models.HubManager) func(w http.ResponseWriter, r *http.Request) {
	return func(w http.ResponseWriter, r *http.Request) {
		clientId := r.Context().Value(auth.UserIdKey).(string)
		hubId := r.URL.Query().Get("hubId")

		log.Printf("WS connect attempt: clientId=%s hubId=%s origin=%s remote=%s", clientId, hubId, r.Header.Get("Origin"), r.RemoteAddr)

		if clientId == "" || hubId == "" {
			log.Printf("WS connect rejected (bad request): clientId=%q hubId=%q", clientId, hubId)
			w.WriteHeader(http.StatusBadRequest)
			_ = json.NewEncoder(w).Encode(map[string]string{"error": "clientId and hubId are required"})
			return
		}

		hasAccess, err := docmanagercomm.CheckAccessRequest(hubId, clientId)
		if err != nil {
			log.Printf("WS connect rejected (access check error): clientId=%s hubId=%s err=%v", clientId, hubId, err)
			w.WriteHeader(http.StatusForbidden)
			_ = json.NewEncoder(w).Encode(map[string]string{"error": "access check failed"})
			return
		}
		if !hasAccess {
			log.Printf("WS connect rejected (access denied): clientId=%s hubId=%s", clientId, hubId)
			w.WriteHeader(http.StatusForbidden)
			_ = json.NewEncoder(w).Encode(map[string]string{"error": "access denied"})
			return
		}

		if !manager.HubExists(hubId) {
			log.Printf("WS hub not in memory; attempting recovery: hubId=%s clientId=%s", hubId, clientId)
			err := manager.LoadExistingDocument(hubId)
			if err != nil {
				log.Printf("WS connect rejected (recover failed): hubId=%s clientId=%s err=%v", hubId, clientId, err)
				w.WriteHeader(http.StatusInternalServerError)
				_ = json.NewEncoder(w).Encode(map[string]string{"error": "failed to recover document state"})
				return
			}
		}

		conn, err := upgrader.Upgrade(w, r, nil)
		if err != nil {
			log.Printf("WS upgrade error: clientId=%s hubId=%s err=%v", clientId, hubId, err)
			return
		}

		log.Printf("WS upgraded: clientId=%s hubId=%s", clientId, hubId)
		manager.ConnectToDocument(clientId, hubId, conn)
	}
}

func GetDocumentStateHandler(manager *models.HubManager) func(w http.ResponseWriter, r *http.Request) {
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

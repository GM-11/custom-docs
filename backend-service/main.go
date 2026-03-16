package main

import (
	"fmt"
	"log"
	"net/http"

	"custom_docs.com/m/v2/models"
	"github.com/gorilla/websocket"
)

var upgrader = websocket.Upgrader{

	CheckOrigin: func(r *http.Request) bool {
		return true // Allowing all origins for development
	},
}

func handleWebSocket(hub *models.Hub) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		conn, err := upgrader.Upgrade(w, r, nil)
		if err != nil {
			log.Println("Upgrade error:", err)
			return
		}

		fmt.Println("Client connected!")

		client := models.Client{}
		client.WsConn = conn
		client.OutboundChannel = make(chan models.ChannelData)
		client.Hub = hub
		client.ClientId = r.URL.Query().Get("clientId")

		hub.Register <- &client

		go client.WritePump()
		client.ReadPump()
	}
}

func main() {
	hub := models.Hub{}
	go hub.Run()
	http.HandleFunc("/ws", handleWebSocket(&hub))
	fmt.Println("Server started on :8080")
	log.Fatal(http.ListenAndServe(":8080", nil))
}

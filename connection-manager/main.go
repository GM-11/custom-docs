package main

import (
	"fmt"
	"log"
	"net/http"
	"os"

	"custom_docs.com/m/v2/auth"
	"custom_docs.com/m/v2/handlers"
	"custom_docs.com/m/v2/messages"
	"custom_docs.com/m/v2/models"
	"github.com/segmentio/kafka-go"
)

func withCORS(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		origin := r.Header.Get("Origin")
		// For MVP: reflect Origin (or hardcode http://localhost:5173)
		if origin != "" {
			w.Header().Set("Access-Control-Allow-Origin", origin)
			w.Header().Set("Vary", "Origin")
		} else {
			w.Header().Set("Access-Control-Allow-Origin", "*")
		}

		w.Header().Set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
		w.Header().Set("Access-Control-Allow-Headers", "Authorization, Content-Type")
		// If you ever use cookies, you'll need:
		// w.Header().Set("Access-Control-Allow-Credentials", "true")

		if r.Method == http.MethodOptions {
			w.WriteHeader(http.StatusNoContent)
			return
		}

		next.ServeHTTP(w, r)
	})
}

func main() {
	mux := http.NewServeMux()
	err := auth.FetchPublicKey()
	if err != nil {
		log.Fatal("Failed to fetch public key:", err)
	}

	kafkaProducer := messages.KafkaProducer{
		Writer: &kafka.Writer{
			Addr:     kafka.TCP(os.Getenv("KAFKA_ADDRESS")),
			Topic:    "document-ops",
			Balancer: &kafka.LeastBytes{},
		},
	}
	defer kafkaProducer.Close()
	manager := models.HubManager{
		Hubs:     make(map[string]*models.Hub),
		Producer: &kafkaProducer,
	}

	mux.Handle("POST /documents", auth.AuthMiddleware(http.HandlerFunc(handlers.CreateNewDocumentHandler(&manager))))
	mux.Handle("GET /ws", auth.AuthMiddleware(http.HandlerFunc(handlers.ConnectToDocumentHandler(&manager))))
	mux.Handle("GET /document", auth.AuthMiddleware(http.HandlerFunc(handlers.GetDocumentStateHandler(&manager))))

	fmt.Println("Server started on :8080")
	log.Fatal(http.ListenAndServe(":8080", withCORS(mux)))
}

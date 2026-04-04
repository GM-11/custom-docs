package docmanagercomm

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"os"
	"strings"
)

func CreateDocumentRequest(title, clientId string) (string, error) {
	docManagerEndpoint := os.Getenv("DOCMANAGER_SERVICE_ENDPOINT")
	body, err := json.Marshal(map[string]any{
		"title":  title,
		"userId": clientId,
	})
	if err != nil {
		return "", fmt.Errorf("failed to marshal request body: %w", err)
	}
	url := fmt.Sprintf("%s/documents", docManagerEndpoint)
	fmt.Printf("Sending POST request to %s with body: %s\n", url, string(body))
	res, err := http.Post(
		url,
		"application/json",
		bytes.NewBuffer(body),
	)
	if err != nil {
		return "", fmt.Errorf("failed to create document: %w", err)
	}
	defer res.Body.Close()

	if res.StatusCode != http.StatusOK {
		return "", fmt.Errorf("failed to create document, status code: %d", res.StatusCode)
	}

	responseBytes, err := io.ReadAll(res.Body)
	if err != nil {
		return "", fmt.Errorf("failed to read response body: %w", err)
	}
	createdHubId := strings.TrimSpace(string(responseBytes))
	return createdHubId, nil
}

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

func CreateDocumentRequest(title, clientId, authHeader string) (string, error) {
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

	req, err := http.NewRequest(http.MethodPost, url, bytes.NewBuffer(body))
	if err != nil {
		return "", fmt.Errorf("failed to create request: %w", err)
	}
	req.Header.Set("Content-Type", "application/json")
	if strings.TrimSpace(authHeader) != "" {
		req.Header.Set("Authorization", authHeader)
	}

	res, err := http.DefaultClient.Do(req)
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

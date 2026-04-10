package docmanagercomm

import (
	"encoding/json"
	"fmt"
	"log"
	"net/http"
	"os"
	"strings"
)

func CheckAccessRequest(documentId, userId, authHeader string) (bool, error) {
	docManagerEndpoint := os.Getenv("DOCMANAGER_SERVICE_ENDPOINT")

	url := fmt.Sprintf("%s/documents/access?documentId=%s&userId=%s", docManagerEndpoint, documentId, userId)

	fmt.Printf("Sending GET request to %s\n", url)

	req, err := http.NewRequest(http.MethodGet, url, nil)

	if err != nil {
		return false, fmt.Errorf("failed to check access: %w", err)
	}

	req.Header.Set("Content-Type", "application/json")
	if strings.TrimSpace(authHeader) != "" {
		req.Header.Set("Authorization", authHeader)
	}

	res, err := http.DefaultClient.Do(req)
	if err != nil {
		return false, fmt.Errorf("failed to check access: %w", err)
	}
	defer res.Body.Close()

	log.Printf("Received access check response: status code=%d", res.StatusCode)
	if res.StatusCode != http.StatusOK {
		return false, fmt.Errorf("access check failed, status code: %d", res.StatusCode)
	}

	var response struct {
		HasAccess bool `json:"hasAccess"`
	}
	if err := json.NewDecoder(res.Body).Decode(&response); err != nil {
		return false, fmt.Errorf("failed to decode access response: %w", err)
	}
	defer res.Body.Close()

	return response.HasAccess, nil
}

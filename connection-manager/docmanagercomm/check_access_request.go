package docmanagercomm

import (
	"encoding/json"
	"fmt"
	"net/http"
	"os"
)

func CheckAccessRequest(documentId, userId string) (bool, error) {
	docManagerEndpoint := os.Getenv("DOCMANAGER_SERVICE_ENDPOINT")

	res, err := http.Get(fmt.Sprintf("%s/documents/access?documentId=%s&userId=%s", docManagerEndpoint, documentId, userId))

	if err != nil {
		return false, fmt.Errorf("failed to check access: %w", err)
	}

	if res.StatusCode != http.StatusOK {
		return false, nil
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

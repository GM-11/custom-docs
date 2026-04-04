package auth

import (
	"crypto/rsa"
	"encoding/base64"
	"encoding/json"
	"fmt"
	"log"
	"math/big"
	"net/http"
	"os"
)

var CachedPublicKey *rsa.PublicKey

func FetchPublicKey() error {

	var response struct {
		Keys []struct {
			N   string `json:"n"`
			Kty string `json:"kty"`
			E   string `json:"e"`
			Kid string `json:"kid"`
		} `json:"keys"`
	}

	authEndpoint := os.Getenv("AUTHENTICATION_SERVICE_ENDPOINT")
	resp, err := http.Get(fmt.Sprintf("%s/.well-known/jwks.json", authEndpoint))

	if err != nil {
		log.Println("Error fetching public key:", err)
		return err
	}
	defer resp.Body.Close()

	err = json.NewDecoder(resp.Body).Decode(&response)
	if err != nil {
		log.Println("Error decoding public key response:", err)
		return err
	}

	nBytes, err := base64.RawURLEncoding.DecodeString(response.Keys[0].N)
	if err != nil {
		return err
	}
	eBytes, err := base64.RawURLEncoding.DecodeString(response.Keys[0].E)
	if err != nil {
		return err
	}

	n := new(big.Int).SetBytes(nBytes)
	e := new(big.Int).SetBytes(eBytes)

	publicKey := &rsa.PublicKey{
		N: n,
		E: int(e.Int64()),
	}

	CachedPublicKey = publicKey

	fmt.Println("Fetched Pubkey successfully")

	return nil
}

package models

import "custom_docs.com/m/v2/engine"

type DocumentState struct {
	Version    int
	Content    string
	Operations []engine.Operation
	Id         string
}

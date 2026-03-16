package models

import "custom_docs.com/m/v2/engine"

type ChannelData struct {
	Operation engine.Operation
	ClientId  string
	Version   int
}

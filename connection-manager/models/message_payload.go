package models

import "custom_docs.com/m/v2/engine"

type MessagePayload interface {
	isMessagePayload()
}

type OperationPayload struct {
	Operation engine.Operation
}

func (o OperationPayload) isMessagePayload() {}
func (d DocumentState) isMessagePayload()    {}

package messages

type OperationMessage struct {
	Type          string `json:"type"`
	OperationId   string `json:"operationId"`
	DocumentId    string `json:"documentId"`
	UserId        string `json:"userId"`
	LamportClock  int64  `json:"lamportClock"`
	OperationData string `json:"operationData"`
	Timestamp     int64  `json:"timestamp"`
}

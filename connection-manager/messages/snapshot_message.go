package messages

type SnapshotMessage struct {
	Type           string `json:"type"`
	DocumentId     string `json:"documentId"`
	DocumentString string `json:"documentString"`
	LamportClock   int64  `json:"lamportClock"`
	Timestamp      int64  `json:"timestamp"`
}

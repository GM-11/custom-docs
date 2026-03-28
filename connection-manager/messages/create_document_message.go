package messages

type CreateDocumentMessage struct {
	UserId string `json:"userId"`
	Title  string `json:"title"`
}

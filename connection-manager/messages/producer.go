package messages

import (
	"context"
	"encoding/json"

	"github.com/segmentio/kafka-go"
)

type KafkaProducer struct {
	Writer *kafka.Writer
}

func (kp *KafkaProducer) PublishOperation(msg OperationMessage) error {
	messageBytes, err := json.Marshal(msg)
	if err != nil {
		return err
	}

	return kp.Writer.WriteMessages(context.Background(), kafka.Message{
		Key:   []byte(msg.DocumentId),
		Value: messageBytes,
	})

}

func (kp *KafkaProducer) PublishSnapshot(msg SnapshotMessage) error {
	messageBytes, err := json.Marshal(msg)
	if err != nil {
		return err
	}

	return kp.Writer.WriteMessages(context.Background(), kafka.Message{
		Key:   []byte(msg.DocumentId),
		Value: messageBytes,
	})
}

func (kp *KafkaProducer) PublishCreateDocument(msg CreateDocumentMessage) error {
	messageBytes, err := json.Marshal(msg)
	if err != nil {
		return err
	}

	return kp.Writer.WriteMessages(context.Background(), kafka.Message{
		Key:   []byte(msg.UserId),
		Value: messageBytes,
	})
}

func (kp *KafkaProducer) Close() error {
	return kp.Writer.Close()
}

package models

import (
	"context"
	"fmt"
	"time"

	"custom_docs.com/m/v2/engine"
	"custom_docs.com/m/v2/grpc/document"
	"google.golang.org/grpc"
	"google.golang.org/grpc/credentials/insecure"
)

func recoverDocumentState(docId string) (string, int64, error) {
	conn, err := grpc.NewClient(
		"localhost:9090",
		grpc.WithTransportCredentials(insecure.NewCredentials()),
	)
	if err != nil {
		return "", -1, fmt.Errorf("failed to connect to Java gRPC server: %w", err)
	}
	defer conn.Close()

	client := document.NewDocumentRecoveryServiceClient(conn)
	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()

	snapshotRes, err := client.GetLatestSnapshot(ctx, &document.GetLatestSnapshotRequest{
		DocumentId: docId,
	})
	if err != nil {
		return "", -1, fmt.Errorf("GetLatestSnapshot failed: %w", err)
	}

	docText := snapshotRes.DocumentText
	fromClock := snapshotRes.LamportClock

	opsRes, err := client.GetOperationsSince(ctx, &document.GetOperationsSinceRequest{
		DocumentId: docId,
		FromClock:  fromClock,
	})
	if err != nil {
		return "", -1, fmt.Errorf("GetOperationsSince failed: %w", err)
	}

	var lastClock int64 = 0

	for _, op := range opsRes.Operations {
		var opType int
		switch op.Type {
		case "insert":
			opType = 0
		case "delete":
			opType = 1
		default:
			return "", -1, fmt.Errorf("unknown operation type: %s", op.Type)
		}
		engineOp := engine.Operation{
			Position: int(op.Position),
			Type:     opType,
			Data:     op.Text,
		}

		if op.LamportClock > lastClock {
			lastClock = op.LamportClock
		}

		docText = engine.ApplyOperation(docText, engineOp)
	}

	return docText, lastClock, nil

}

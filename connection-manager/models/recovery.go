package models

import (
	"context"
	"fmt"
	"log"
	"os"
	"time"

	"custom_docs.com/m/v2/engine"
	"custom_docs.com/m/v2/grpc/document"
	"google.golang.org/grpc"
	"google.golang.org/grpc/credentials/insecure"
)

func recoverDocumentState(docId string) (string, int64, error) {
	conn, err := grpc.NewClient(
		os.Getenv("GRPC_URL"),
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
		var opData any

		switch op.Type {
		case "insert":
			opType = engine.INSERT
			opData = op.Text
		case "delete":
			opType = engine.DELETE
			opData = int(op.Position)
		default:
			return "", -1, fmt.Errorf("unknown operation type: %s", op.Type)
		}
		engineOp := engine.Operation{
			Position: int(op.Position),
			Type:     opType,
			Data:     opData,
		}

		if engineOp.Type == engine.DELETE {
			log.Printf(
				"recovery delete skipped: docId=%s opId=%s pos=%d (no length field available in replay payload)",
				docId,
				op.OperationId,
				engineOp.Position,
			)
			continue
		}

		nextText, err := engine.ApplyOperationSafe(docText, engineOp)
		if err != nil {
			log.Printf(
				"recovery apply skipped: docId=%s err=%v opType=%d pos=%d data=%v",
				docId,
				err,
				engineOp.Type,
				engineOp.Position,
				engineOp.Data,
			)
			continue
		}
		docText = nextText

		if op.LamportClock > lastClock {
			lastClock = op.LamportClock
		}
	}

	return docText, lastClock, nil

}

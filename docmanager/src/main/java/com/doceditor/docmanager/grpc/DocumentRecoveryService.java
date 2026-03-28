package com.doceditor.docmanager.grpc;

import com.doceditor.docmanager.entity.Operations;
import com.doceditor.docmanager.repository.OperationRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.grpc.stub.StreamObserver;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DocumentRecoveryService extends DocumentRecoveryServiceGrpc.DocumentRecoveryServiceImplBase {

    private final OperationRepository operationRepository;
    private final ObjectMapper objectMapper;

    public DocumentRecoveryService(OperationRepository operationRepository) {
        this.operationRepository = operationRepository;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public void getLatestSnapshot(
            DocumentRecoveryProto.GetLatestSnapshotRequest request,
            StreamObserver<DocumentRecoveryProto.GetLatestSnapshotResponse> responseObserver) {

        DocumentRecoveryProto.GetLatestSnapshotResponse response = DocumentRecoveryProto.GetLatestSnapshotResponse
                .newBuilder()
                .setDocumentText("")
                .setLamportClock(0)
                .setFound(false)
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void getOperationsSince(
            DocumentRecoveryProto.GetOperationsSinceRequest request,
            StreamObserver<DocumentRecoveryProto.GetOperationsSinceResponse> responseObserver) {

        String documentId = request.getDocumentId();
        long fromClock = request.getFromClock();

        List<Operations> ops = operationRepository.findOperationsSinceSnapshot(documentId, fromClock);

        DocumentRecoveryProto.GetOperationsSinceResponse.Builder responseBuilder = DocumentRecoveryProto.GetOperationsSinceResponse
                .newBuilder();

        for (Operations op : ops) {
            try {
                JsonNode data = objectMapper.readTree(op.getOperationData());

                DocumentRecoveryProto.Operation protoOp = DocumentRecoveryProto.Operation
                        .newBuilder()
                        .setOperationId(op.getId())
                        .setLamportClock(op.getLamportClock())
                        .setPosition(data.get("position").asInt())
                        .setType(data.get("type").asText())
                        .setText(data.get("text").asText())
                        .build();

                responseBuilder.addOperations(protoOp);

            } catch (Exception e) {
                System.err.println(
                        "Failed to parse operation_data for op: " + op.getId() + " — " + e.getMessage());
            }
        }

        responseObserver.onNext(responseBuilder.build());
        responseObserver.onCompleted();
    }
}

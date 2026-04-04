package com.doceditor.docmanager.grpc;

import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.doceditor.docmanager.entity.Operations;
import com.doceditor.docmanager.entity.Snapshot;
import com.doceditor.docmanager.repository.OperationRepository;
import com.doceditor.docmanager.repository.SnapshotRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import io.grpc.stub.StreamObserver;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

@Service
public class DocumentRecoveryService extends DocumentRecoveryServiceGrpc.DocumentRecoveryServiceImplBase {

    private final OperationRepository operationRepository;
    private final ObjectMapper objectMapper;
    private final SnapshotRepository snapshotRepository;
    private final BlobServiceClient blobServiceClient;

    public DocumentRecoveryService(OperationRepository operationRepository,
            SnapshotRepository snapshotRepository,
            BlobServiceClient blobServiceClient) {
        this.operationRepository = operationRepository;
        this.objectMapper = new ObjectMapper();
        this.snapshotRepository = snapshotRepository;
        this.blobServiceClient = blobServiceClient;
    }

    @Value("${azure.storage.container-name}")
    private String containerName;

    @Override
    public void getLatestSnapshot(
            DocumentRecoveryProto.GetLatestSnapshotRequest request,
            StreamObserver<DocumentRecoveryProto.GetLatestSnapshotResponse> responseObserver) {

        String documentId = request.getDocumentId();

        try {
            Optional<Snapshot> latestSnapshot = snapshotRepository
                    .findLatestSnapshotByDocumentId(UUID.fromString(documentId));

            if (latestSnapshot.isPresent()) {
                Snapshot snapshot = latestSnapshot.get();
                String blobUrl = snapshot.getS3Url();

                String blobName = blobUrl.substring(blobUrl.indexOf(containerName) + containerName.length() + 1);
                blobName = java.net.URLDecoder.decode(blobName, StandardCharsets.UTF_8);

                BlobContainerClient containerClient = blobServiceClient.getBlobContainerClient(containerName);
                String documentText = containerClient.getBlobClient(blobName).downloadContent().toString();

                String fileName = blobName.substring(blobName.lastIndexOf("/") + 1).replace(".txt", "");
                long lamportClock = Long.parseLong(fileName);

                DocumentRecoveryProto.GetLatestSnapshotResponse response = DocumentRecoveryProto.GetLatestSnapshotResponse
                        .newBuilder()
                        .setDocumentText(documentText)
                        .setLamportClock(lamportClock)
                        .setFound(true)
                        .build();
                responseObserver.onNext(response);
                responseObserver.onCompleted();
                return;
            }
        } catch (Exception e) {
            System.err.println("Error retrieving snapshot for document " + documentId + ": " + e.getMessage());
        }

        // fallback: no snapshot found or error
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

        DocumentRecoveryProto.GetOperationsSinceResponse.Builder responseBuilder = DocumentRecoveryProto.GetOperationsSinceResponse
                .newBuilder();

        try {
            // Parse the document ID and fetch operations. Wrap in try/catch so that
            // invalid UUID or JDBC type-mapping issues don't crash the gRPC call.
            List<Operations> ops = operationRepository.findOperationsSinceSnapshot(UUID.fromString(documentId),
                    fromClock);

            for (Operations op : ops) {
                try {
                    JsonNode data = objectMapper.readTree(op.getOperationData());

                    DocumentRecoveryProto.Operation protoOp = DocumentRecoveryProto.Operation
                            .newBuilder()
                            .setOperationId(op.getId().toString())
                            .setLamportClock(op.getLamportClock())
                            .setPosition(data.path("position").asInt())
                            .setType(data.path("type").asText())
                            .setText(data.path("text").asText())
                            .build();

                    responseBuilder.addOperations(protoOp);

                } catch (Exception e) {
                    // If an individual operation's JSON is malformed, skip it but don't fail the
                    // whole call
                    System.err.println("Failed to parse operation_data for op: " + op.getId() + " — " + e.getMessage());
                }
            }
        } catch (IllegalArgumentException | ClassCastException e) {
            // IllegalArgumentException can come from UUID.fromString(documentId)
            // ClassCastException can arise from JDBC/driver type-mapping (UUID vs String)
            // In either case, return an empty response instead of throwing.
            System.err.println("Could not recover operations for document " + documentId + ": " + e.getMessage());
        } catch (Exception e) {
            // Any other unexpected error should also not crash the service call — log and
            // return empty.
            System.err.println(
                    "Unexpected error while recovering operations for document " + documentId + ": " + e.getMessage());
        }

        responseObserver.onNext(responseBuilder.build());
        responseObserver.onCompleted();
    }
}

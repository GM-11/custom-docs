package com.doceditor.docmanager.services;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.doceditor.docmanager.entity.DocumentAccess;
import com.doceditor.docmanager.entity.DocumentAccessEmbeddedClass;
import com.doceditor.docmanager.entity.Documents;
import com.doceditor.docmanager.entity.Operations;
import com.doceditor.docmanager.entity.Snapshot;
import com.doceditor.docmanager.kafka.dto.OperationMessage;
import com.doceditor.docmanager.kafka.dto.SnapshotMessage;
import com.doceditor.docmanager.repository.DocumentsAccessRepository;
import com.doceditor.docmanager.repository.DocumentsRepository;
import com.doceditor.docmanager.repository.OperationRepository;
import com.doceditor.docmanager.repository.SnapshotRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class DocumentService {

    @Autowired
    private DocumentsRepository documentsRepository;

    @Autowired
    private BlobServiceClient blobServiceClient;

    @Autowired
    private SnapshotRepository snapshotRepository;

    @Autowired
    private OperationRepository operationRepository;

    @Autowired
    private DocumentsAccessRepository documentsAccessRepository;

    @Value("${azure.storage.container-name}")
    private String containerName;

    private static final Logger logger = LoggerFactory.getLogger(DocumentService.class);

    public List<Documents> getUserDocuments(String userId) {
        return documentsAccessRepository.findDocumentsByUserId(UUID.fromString(userId));
        // return documentsRepository.findD(UUID.fromString(userId));
    }

    public String createNewDocument(String userId, String title) {
        Documents d = new Documents(UUID.fromString(userId), title);
        documentsRepository.save(d);
        UUID documentId = d.getId();
        UUID uuidUserId = UUID.fromString(userId);

        DocumentAccess access = new DocumentAccess(new DocumentAccessEmbeddedClass(documentId, uuidUserId), "owner");
        documentsAccessRepository.save(access);
        return documentId.toString();
    }

    public String grantAccess(String documentId, String userId, String ownerId) {
        if (!documentsAccessRepository.userIsOwnerOfDocument(UUID.fromString(documentId), UUID.fromString(ownerId))) {
            logger.warn("User {} is not owner of document {}", ownerId, documentId);
            return "User does not have permission to grant access";
        }

        DocumentAccess access = new DocumentAccess(
                new DocumentAccessEmbeddedClass(UUID.fromString(documentId), UUID.fromString(userId)), "editor");
        documentsAccessRepository.save(access);
        return "Access granted successfully";
    }

    public boolean checkDocumentAccess(String documentId, String userId) {
        if (documentsAccessRepository.userIsEditorOfDocument(UUID.fromString(documentId), UUID.fromString(userId))) {
            return true;
        } else {
            return false;
        }
    }

    public void processOperation(OperationMessage message) {
        try {

            if (!documentsAccessRepository.userIsEditorOfDocument(UUID.fromString(message.getDocumentId()),
                    UUID.fromString(message.getUserId()))) {
                logger.warn("User {} does not access to document {}", message.getUserId(), message.getDocumentId());
                return;
            }

            // convert string to uuid
            UUID operationId = UUID.fromString(message.getOperationId());

            if (operationRepository.findById(operationId).isPresent()) {
                logger.warn("Duplicate operation received with ID: {}", message.getOperationId());
                return;
            }

            Operations o = new Operations(operationId, UUID.fromString(message.getDocumentId()),
                    UUID.fromString(message.getUserId()), message.getLamportClock(), message.getOperationData(),
                    LocalDateTime.now());

            operationRepository.save(o);
        } catch (Exception e) {
            logger.error("Error processing operation message: {}", message, e);
        }

    }

    public void processSnapshot(SnapshotMessage message) {
        try {

            BlobContainerClient client = blobServiceClient.getBlobContainerClient(containerName);

            String blobName = message.getDocumentId() + "/" + message.getLamportClock() + ".txt";

            BlobClient blobClient = client.getBlobClient(blobName);

            byte[] documentBytes = message.getDocumentString().getBytes(StandardCharsets.UTF_8);

            blobClient.upload(new ByteArrayInputStream(documentBytes), documentBytes.length, true);
            String blobUrl = blobClient.getBlobUrl();

            Snapshot s = new Snapshot(UUID.fromString(message.getDocumentId()), blobUrl);

            snapshotRepository.save(s);
        } catch (Exception e) {
            logger.error("Error processing snapshot message: {}", message, e);
        }
    }

}

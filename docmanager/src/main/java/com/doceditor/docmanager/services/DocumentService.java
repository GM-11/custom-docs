package com.doceditor.docmanager.services;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class DocumentService {

    @Value("${spring.env.auth-url}")
    private String authUrl;

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

    public String grantAccess(String documentId, String userEmail, String ownerId, String authHeader) {
        UUID documentUuid;
        UUID ownerUuid;

        try {
            documentUuid = UUID.fromString(documentId);
            ownerUuid = UUID.fromString(ownerId);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid UUID format for documentId {} or ownerId {}", documentId, ownerId, e);
            return "Invalid documentId or ownerId";
        }

        // 1. Check that owner is indeed the owner of the document
        boolean isOwner = documentsAccessRepository.userIsOwnerOfDocument(documentUuid, ownerUuid);
        if (!isOwner) {
            logger.warn("User {} is not owner of document {}", ownerId, documentId);
            return "User does not have permission to grant access";
        }

        String userId = null;

        try {
            // 2. Fetch user id from auth service by userEmail
            URL url = URI.create(authUrl + "/auth/id?userEmail=" + userEmail).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            try {
                conn.setRequestMethod("GET");
                conn.setDoOutput(false);
                conn.setRequestProperty("Accept", "application/json");

                // Forward the caller's bearer token so /auth/id is not publicly enumerable
                if (authHeader != null && !authHeader.isBlank()) {
                    conn.setRequestProperty("Authorization", authHeader);
                }

                int responseCode = conn.getResponseCode();
                if (responseCode >= 400) {
                    logger.warn("Auth request returned {} for document {} and userEmail {}", responseCode,
                            documentId, userEmail);
                }

                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder responseBuilder = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        responseBuilder.append(line);
                    }

                    String responseBody = responseBuilder.toString();
                    try {
                        ObjectMapper mapper = new ObjectMapper();
                        JsonNode root = mapper.readTree(responseBody);
                        JsonNode userIdNode = root.get("userId");
                        if (userIdNode != null && !userIdNode.isNull()) {
                            userId = userIdNode.asText();
                        } else {
                            logger.warn(
                                    "userId field missing or null in auth response for document {} and userEmail {}. Response body: {}",
                                    documentId, userEmail, responseBody);
                        }
                    } catch (Exception jsonEx) {
                        logger.error(
                                "Failed to parse auth service JSON response for document {} and userEmail {}. Response body: {}",
                                documentId, userEmail, responseBody, jsonEx);
                    }
                } catch (Exception ioEx) {
                    logger.error("Error reading auth service response for document {} and userEmail {}", documentId,
                            userEmail, ioEx);
                }
            } finally {
                conn.disconnect();
            }
        } catch (Exception e) {
            logger.error("Error calling auth service for grantAccess: document={}, userEmail={}, owner={}",
                    documentId, userEmail, ownerId, e);
        }

        if (userId == null || userId.isEmpty()) {
            logger.warn("Auth service did not return a valid userId for document {} and userEmail {}",
                    documentId, userEmail);
            return "Failed to resolve userId from auth service";
        }

        // 3. Create document access for resolved userId and the document
        UUID userUuid;
        try {
            userUuid = UUID.fromString(userId);
        } catch (IllegalArgumentException e) {
            logger.warn("Auth service returned invalid userId {} for userEmail {}", userId, userEmail, e);
            return "Auth service returned invalid userId";
        }

        DocumentAccess access = new DocumentAccess(
                new DocumentAccessEmbeddedClass(documentUuid, userUuid), "editor");
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

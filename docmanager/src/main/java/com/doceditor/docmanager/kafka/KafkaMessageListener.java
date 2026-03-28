package com.doceditor.docmanager.kafka;

import com.doceditor.docmanager.kafka.dto.NewDocumentMessage;
import com.doceditor.docmanager.kafka.dto.OperationMessage;
import com.doceditor.docmanager.kafka.dto.SnapshotMessage;
import com.doceditor.docmanager.services.DocumentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

@Service
public class KafkaMessageListener {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DocumentService documentService;

    private static final Logger logger = LoggerFactory.getLogger(KafkaMessageListener.class);

    @KafkaListener(topics = "document-ops", groupId = "docmanager-group")
    public void listen(@Payload String message, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        System.out.println("Received message: " + message);
        try {
            var jsonNode = objectMapper.readTree(message);
            String messageType = jsonNode.get("type").asText();

            if ("operation".equals(messageType)) {
                OperationMessage opMsg = objectMapper.treeToValue(jsonNode, OperationMessage.class);
                logger.info("Processing operation: {}", opMsg.getOperationId());
                documentService.processOperation(opMsg);
            } else if ("snapshot".equals(messageType)) {
                SnapshotMessage snapMsg = objectMapper.treeToValue(jsonNode, SnapshotMessage.class);
                logger.info("Processing snapshot for document: {}", snapMsg.getDocumentId());
                documentService.processSnapshot(snapMsg);
            } else if ("new_document".equals(messageType)) {
                NewDocumentMessage newDocMsg = objectMapper.treeToValue(jsonNode, NewDocumentMessage.class);
                logger.info("Processing creating new document");
                documentService.createNewDocument(newDocMsg);
            } else {
                logger.warn("Unknown message type: {}", messageType);
            }
        } catch (Exception e) {
            logger.error("Error processing Kafka message: {}", message, e);
        }
    }
}

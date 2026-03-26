package com.doceditor.docmanager.services;

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
import org.springframework.stereotype.Service;

@Service
public class DocumentService {

    @Autowired
    private DocumentsRepository documentsRepository;

    @Autowired
    private SnapshotRepository snapshotRepository;

    @Autowired
    private OperationRepository operationRepository;

    @Autowired
    private DocumentsAccessRepository documentsAccessRepository;

    private static final Logger logger = LoggerFactory.getLogger(DocumentService.class);

    public void processOperation(OperationMessage message) {
        try {

            if (!documentsAccessRepository.userIsEditorOfDocument(message.getDocumentId(), message.getUserId())) {
                logger.warn("User {} does not access to document {}", message.getUserId(), message.getDocumentId());
                return;
            }

            if (operationRepository.findByOperationId(message.getOperationId()).isPresent()) {
                logger.warn("Duplicate operation received with ID: {}", message.getOperationId());
                return;
            }

            Operations o = new Operations(message.getOperationId(), message.getDocumentId(), message.getUserId(),
                    message.getLamportClock(), message.getOperationData());

            operationRepository.save(o);
        } catch (Exception e) {
            logger.error("Error processing operation message: {}", message, e);
        }

    }

    public void processSnapshot(SnapshotMessage message) {
        try {
            /// TODO: write document to s3
            String documentPath = "/local/" + message.getDocumentId();
            Snapshot s = new Snapshot(message.getDocumentId(), documentPath, message.getOperationId());


            snapshotRepository.save(s);
        } catch (Exception e) {
            logger.error("Error processing snapshot message: {}", message, e);
        }
    }
}

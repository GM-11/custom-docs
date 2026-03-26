package com.doceditor.docmanager.kafka.dto;

public class SnapshotMessage {
    private String type;
    private String documentId;
    private String documentString;
    private Long lamportClock;
    private Long timestamp;
    private String operationId;

    public SnapshotMessage() {
    }

    public SnapshotMessage(String documentId, String documentString, Long lamportClock, Long timestamp,
            String operationId) {
        this.type = "snapshot";
        this.documentId = documentId;
        this.documentString = documentString;
        this.lamportClock = lamportClock;
        this.timestamp = timestamp;
        this.operationId = operationId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getDocumentId() {
        return documentId;
    }

    public void setDocumentId(String documentId) {
        this.documentId = documentId;
    }

    public String getDocumentString() {
        return documentString;
    }

    public void setDocumentString(String documentString) {
        this.documentString = documentString;
    }

    public Long getLamportClock() {
        return lamportClock;
    }

    public void setLamportClock(Long lamportClock) {
        this.lamportClock = lamportClock;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }

    public String getOperationId() {
        return operationId;
    }

    public void setOperationId(String operationId) {
        this.operationId = operationId;
    }
}

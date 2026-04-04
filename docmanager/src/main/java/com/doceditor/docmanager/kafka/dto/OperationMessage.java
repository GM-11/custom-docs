package com.doceditor.docmanager.kafka.dto;

public class OperationMessage {

    private String type;
    private String operationId;
    private String documentId;
    private String userId;
    private Long lamportClock;
    private String operationData;
    private Long timestamp;

    public OperationMessage() {
    }

    public OperationMessage(String operationId, String documentId, String userId, Long lamportClock,
            String operationData, Long timestamp) {
        this.type = "operation";
        this.operationId = operationId;
        this.documentId = documentId;
        this.userId = userId;
        this.lamportClock = lamportClock;
        this.operationData = operationData;
        this.timestamp = timestamp;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getOperationId() {
        return operationId;
    }

    public void setOperationId(String operationId) {
        this.operationId = operationId;
    }

    public String getDocumentId() {
        return documentId;
    }

    public void setDocumentId(String documentId) {
        this.documentId = documentId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public Long getLamportClock() {
        return lamportClock;
    }

    public void setLamportClock(Long lamportClock) {
        this.lamportClock = lamportClock;
    }

    public String getOperationData() {
        return operationData;
    }

    public void setOperationData(String operationData) {
        this.operationData = operationData;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }

}

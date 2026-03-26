package com.doceditor.docmanager.entity;

import java.time.LocalDateTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "operations", indexes = {
        @Index(name = "idx_operations_document_id", columnList = "document_id"),
        @Index(name = "idx_operations_lamport_clock", columnList = "document_id, lamport_clock"),
        @Index(name = "idx_operations_operation_id", columnList = "operation_id")
})
public class Operations {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private String id;

    @Column(name = "operation_id", nullable = false, unique = true)
    private String operationId;

    @Column(name = "document_id", nullable = false)
    private String documentId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "lamport_clock", nullable = false)
    private Long lamportClock;

    @Column(name = "operation_data", nullable = false)
    private String operationData;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public Operations() {
    }

    public Operations(String operationId, String documentId, String userId, Long lamportClock, String operationData) {
        this.operationId = operationId;
        this.documentId = documentId;
        this.userId = userId;
        this.lamportClock = lamportClock;
        this.operationData = operationData;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return "Operations{" +
                "id='" + id + '\'' +
                ", operationId='" + operationId + '\'' +
                ", documentId='" + documentId + '\'' +
                ", userId='" + userId + '\'' +
                ", lamportClock=" + lamportClock +
                ", operationData='" + operationData + '\'' +
                ", createdAt=" + createdAt +
                '}';
    }
}

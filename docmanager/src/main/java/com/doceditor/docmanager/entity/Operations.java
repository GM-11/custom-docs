package com.doceditor.docmanager.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Table;
import jakarta.persistence.Id;
import jakarta.persistence.Index;

@Entity
@Table(name = "operations", indexes = {
        @Index(name = "idx_operations_document_id", columnList = "document_id"),
        @Index(name = "idx_operations_lamport_clock", columnList = "document_id, lamport_clock")
})
public class Operations {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private String id;

    @Column(name = "document_id", nullable = false)
    private String documentId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "lamport_clock", nullable = false)
    private Long lamportClock;

    @Column(name = "operation_data", nullable = false, unique = true)
    private String operationData;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public Operations(String documentId, String userId, Long lamportClock, String operationData) {
        this.documentId = documentId;
        this.userId = userId;
        this.lamportClock = lamportClock;
        this.operationData = operationData;
        this.createdAt = LocalDateTime.now();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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
                ", documentId='" + documentId + '\'' +
                ", userId='" + userId + '\'' +
                ", lamportClock=" + lamportClock +
                ", operationData='" + operationData + '\'' +
                ", createdAt=" + createdAt +
                '}';
    }
}

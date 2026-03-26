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
@Table(name = "snapshots", indexes = {
        @Index(name = "idx_snapshots_document_id", columnList = "document_id")
})
public class Snapshot {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private String id;

    @Column(name = "document_id", nullable = false)
    private String documentId;

    @Column(name = "s3_url", nullable = false)
    private String s3Url;

    @Column(name = "based_on_operation_id", nullable = false)
    private String basedOnOperationId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public Snapshot() {
    }

    public Snapshot(String documentId, String s3Url, String basedOnOperationId) {
        this.documentId = documentId;
        this.s3Url = s3Url;
        this.basedOnOperationId = basedOnOperationId;
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

    public String getDocumentId() {
        return documentId;
    }

    public void setDocumentId(String documentId) {
        this.documentId = documentId;
    }

    public String getS3Url() {
        return s3Url;
    }

    public void setS3Url(String s3Url) {
        this.s3Url = s3Url;
    }

    public String getBasedOnOperationId() {
        return basedOnOperationId;
    }

    public void setBasedOnOperationId(String basedOnOperationId) {
        this.basedOnOperationId = basedOnOperationId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return "Snapshot{" +
                "id='" + id + '\'' +
                ", documentId='" + documentId + '\'' +
                ", s3Url='" + s3Url + '\'' +
                ", basedOnOperationId='" + basedOnOperationId + '\'' +
                ", createdAt=" + createdAt +
                '}';
    }
}

package com.doceditor.docmanager.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "snapshots", indexes = {
        @Index(name = "idx_snapshots_document_id", columnList = "document_id")
})
public class Snapshot {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Column(name = "s3_url", nullable = false)
    private String s3Url;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public Snapshot(UUID documentId, String s3Url) {
        this.documentId = documentId;
        this.s3Url = s3Url;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    @Override
    public String toString() {
        return "Snapshot{" +
                "id='" + id + '\'' +
                ", documentId='" + documentId + '\'' +
                ", s3Url='" + s3Url + '\'' +
                ", createdAt=" + createdAt +
                '}';
    }
}

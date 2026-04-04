package com.doceditor.docmanager.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import org.springframework.data.domain.Persistable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "operations", indexes = {
        @Index(name = "idx_operations_document_id", columnList = "document_id"),
        @Index(name = "idx_operations_lamport_clock", columnList = "document_id, lamport_clock"),
})
public class Operations implements Persistable<UUID> {

    @Id
    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name = "id")
    private UUID id;

    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "lamport_clock", nullable = false)
    private Long lamportClock;

    @Column(name = "operation_data", nullable = false)
    private String operationData;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Transient
    private boolean isNew = true;

    public Operations(UUID id, UUID documentId, UUID userId, Long lamportClock, String operationData,
            LocalDateTime createdAt) {
        this.id = id;
        this.documentId = documentId;
        this.userId = userId;
        this.lamportClock = lamportClock;
        this.operationData = operationData;
        this.createdAt = createdAt;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        this.isNew = false;
    }

    @Override
    public boolean isNew() {
        return isNew;
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

package com.doceditor.docmanager.entity;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class DocumentAccessEmbeddedClass implements Serializable {
    @Column(name = "document_id")
    private UUID documentId;

    @Column(name = "user_id")
    private UUID userId;

    public DocumentAccessEmbeddedClass() {
    }

    public DocumentAccessEmbeddedClass(UUID documentId, UUID userId) {
        this.documentId = documentId;
        this.userId = userId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if ((o instanceof DocumentAccessEmbeddedClass) == false)
            return false;
        DocumentAccessEmbeddedClass that = (DocumentAccessEmbeddedClass) o;
        return userId.equals(that.userId) && documentId.equals(that.documentId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, documentId);
    }
}

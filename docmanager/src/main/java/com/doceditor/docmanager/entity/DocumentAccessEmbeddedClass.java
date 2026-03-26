package com.doceditor.docmanager.entity;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class DocumentAccessEmbeddedClass implements Serializable {
    @Column(name = "document_id")
    private String documentId;

    @Column(name = "user_id")
    private String userId;

    public DocumentAccessEmbeddedClass() {
    }

    public DocumentAccessEmbeddedClass(String documentId, String userId) {
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

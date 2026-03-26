package com.doceditor.docmanager.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(name = "document_access", indexes = @Index(name = "idx_document_access_user_id", columnList = "document_id, user_id"))
public class DocumentAccess {

    @EmbeddedId
    private DocumentAccessEmbeddedClass id;

    @Column(name = "user_role", nullable = false)
    private String userRole; // e.g., "editor", "owner", "viewer"

    public DocumentAccess() {
    }

    public DocumentAccess(DocumentAccessEmbeddedClass id, String userRole) {
        this.id = id;
        this.userRole = userRole;
    }

    public DocumentAccessEmbeddedClass getId() {
        return id;
    }

    public void setId(DocumentAccessEmbeddedClass id) {
        this.id = id;
    }

    public String getUserRole() {
        return userRole;
    }

    public void setUserRole(String userRole) {
        this.userRole = userRole;
    }

}

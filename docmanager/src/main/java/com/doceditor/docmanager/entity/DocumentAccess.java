package com.doceditor.docmanager.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "documents_access", indexes = @Index(name = "idx_document_access_user_id", columnList = "document_id, user_id"))
public class DocumentAccess {

    @EmbeddedId
    private DocumentAccessEmbeddedClass id;

    @Column(name = "user_role", nullable = false)
    private String userRole; // e.g., "editor", "owner", "viewer"

}

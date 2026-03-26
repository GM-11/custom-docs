package com.doceditor.docmanager.repository;

import java.util.List;
import java.util.Optional;

import com.doceditor.docmanager.entity.DocumentAccess;
import com.doceditor.docmanager.entity.DocumentAccessEmbeddedClass;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentsAccessRepository extends JpaRepository<DocumentAccess, DocumentAccessEmbeddedClass> {

    Optional<DocumentAccess> findById(DocumentAccessEmbeddedClass id);

    @Query("SELECT document_id FROM documents_access WHERE user_id = :userId")
    List<String> findDocumentIdsByUserId(@Param("userId") String userId);

    @Query("SELECT user_id FROM documents_access WHERE document_id = :documentId AND user_role in ('editor', 'owner')")
    List<String> findDocumentEditors(@Param("documentId") String documentId);

    @Query("SELECT EXISTS (SELECT 1 FROM documents_access WHERE document_id = :documentId AND user_id = :userId AND user_role in ('owner','editor'));")
    boolean userIsEditorOfDocument(@Param("documentId") String documentId, @Param("userId") String userId);

    @Query("SELECT user_id FROM documents_access WHERE document_id = :documentId AND user_role = 'viewer'")
    List<String> findDocumentViewers(@Param("documentId") String documentid);

}

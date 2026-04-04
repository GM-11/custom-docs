package com.doceditor.docmanager.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.doceditor.docmanager.entity.DocumentAccess;
import com.doceditor.docmanager.entity.DocumentAccessEmbeddedClass;
import com.doceditor.docmanager.entity.Documents;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentsAccessRepository extends JpaRepository<DocumentAccess, DocumentAccessEmbeddedClass> {

    Optional<DocumentAccess> findById(DocumentAccessEmbeddedClass id);

    @Query(value = "SELECT document_id FROM documents_access WHERE user_id = :userId", nativeQuery = true)
    List<String> findDocumentIdsByUserId(@Param("userId") UUID userId);

    @Query(value = "SELECT user_id FROM documents_access WHERE document_id = :documentId AND user_role in ('editor', 'owner')", nativeQuery = true)
    List<String> findDocumentEditors(@Param("documentId") UUID documentId);

    @Query(value = "SELECT EXISTS (SELECT 1 FROM documents_access WHERE document_id = :documentId AND user_id = :userId AND user_role in ('owner','editor'));", nativeQuery = true)
    boolean userIsEditorOfDocument(@Param("documentId") UUID documentId, @Param("userId") UUID userId);

    @Query(value = "SELECT user_id FROM documents_access WHERE document_id = :documentId AND user_role = 'viewer'", nativeQuery = true)
    List<String> findDocumentViewers(@Param("documentId") UUID documentId);

    @Query(value = "SELECT EXISTS (SELECT 1 FROM documents_access WHERE document_id = :documentId AND user_id = :userId AND user_role = 'owner');", nativeQuery = true)
    boolean userIsOwnerOfDocument(UUID documentId, UUID userId);

    @Query(value = "SELECT documents.id, documents.created_at, documents.owner_id, documents.title, documents.updated_at from documents JOIN documents_access ON documents_access.document_id = documents.id WHERE documents_access.user_id = :userId", nativeQuery = true)
    List<Documents> findDocumentsByUserId(UUID userId);

}

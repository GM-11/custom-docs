package com.doceditor.docmanager.repository;

import com.doceditor.docmanager.entity.Snapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;

public interface SnapshotRepository extends JpaRepository<Snapshot, String> {
    @Query(value = "SELECT * FROM snapshots WHERE document_id = :documentId ORDER BY created_at DESC LIMIT 1", nativeQuery = true)
    Optional<Snapshot> findLatestSnapshotByDocumentId(@Param("documentId") UUID documentId);
}

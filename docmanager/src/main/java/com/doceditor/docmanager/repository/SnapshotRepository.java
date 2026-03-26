package com.doceditor.docmanager.repository;

import com.doceditor.docmanager.entity.Snapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface SnapshotRepository extends JpaRepository<Snapshot, String> {
    @Query("SELECT s FROM snapshot s WHERE s.documentId = :documentId ORDER BY s.createdAt DESC LIMIT 1")
    Optional<Snapshot> findLatestSnapshotByDocumentId(@Param("documentId") String documentId);
}

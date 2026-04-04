package com.doceditor.docmanager.repository;

import com.doceditor.docmanager.entity.Operations;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OperationRepository extends JpaRepository<Operations, UUID> {

    Optional<Operations> findById(UUID operationId);

    List<Operations> findByDocumentIdOrderByLamportClockAsc(UUID documentId);

    @Query(value = "SELECT * FROM operations WHERE document_id = CAST(:documentId AS uuid) AND lamport_clock > :lamportClock ORDER BY lamport_clock ASC", nativeQuery = true)
    List<Operations> findOperationsSinceSnapshot(@Param("documentId") UUID documentId,
            @Param("lamportClock") Long lamportClock);
}

package com.doceditor.docmanager.repository;

import com.doceditor.docmanager.entity.Operations;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface OperationRepository extends JpaRepository<Operations, String> {
    Optional<Operations> findById(String operationId);

    List<Operations> findByDocumentIdOrderByLamportClockAsc(String documentId);

    @Query(value = "SELECT * FROM operations WHERE id = CAST(:documentId AS uuid) AND lamport_clock > :lamportClock ORDER BY lamport_clock ASC", nativeQuery = true)
    List<Operations> findOperationsSinceSnapshot(@Param("documentId") String documentId,
            @Param("lamportClock") Long lamportClock);
}

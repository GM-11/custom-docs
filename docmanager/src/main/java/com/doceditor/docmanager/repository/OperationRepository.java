package com.doceditor.docmanager.repository;

import com.doceditor.docmanager.entity.Operations;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface OperationRepository extends JpaRepository<Operations, String> {
    Optional<Operations> findByOperationId(String operationId);

    List<Operations> findByDocumentIdOrderByLamportClockAsc(String documentId);

    @Query(value = "SELECT o FROM operations o WHERE o.documentId = :documentId AND o.lamportClock > :lamportClock ORDER BY o.lamportClock ASC", nativeQuery = true)
    List<Operations> findOperationsSinceSnapshot(@Param("documentId") String documentId,
            @Param("lamportClock") Long lamportClock);
}

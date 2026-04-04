package com.doceditor.docmanager.repository;

import com.doceditor.docmanager.entity.Documents;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface DocumentsRepository extends JpaRepository<Documents, String> {
    Optional<Documents> findByIdAndOwnerId(String id, String ownerId);

    List<Documents> findByOwnerId(String ownerId);
}

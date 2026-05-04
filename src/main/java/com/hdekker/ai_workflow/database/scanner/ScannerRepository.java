package com.hdekker.ai_workflow.database.scanner;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * JPA repository for scanner entities.
 */
@Repository
public interface ScannerRepository extends JpaRepository<ScannerEntity, String> {

    /**
     * Find a scanner by its ID.
     */
    Optional<ScannerEntity> findById(String id);
}

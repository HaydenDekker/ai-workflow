package com.hdekker.ai_workflow.adapter.outbound.persistence.scanner;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * JPA repository for scanner entities.
 */
@Repository
public interface ScannerJpaRepository extends JpaRepository<ScannerEntity, String> {

    /**
     * Find a scanner by its ID.
     */
    Optional<ScannerEntity> findById(String id);
}

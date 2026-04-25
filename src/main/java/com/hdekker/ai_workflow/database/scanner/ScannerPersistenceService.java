package com.hdekker.ai_workflow.database.scanner;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.hdekker.ai_workflow.rest.dto.ScannerInfo;

/**
 * Service layer for scanner persistence operations.
 * Handles CRUD operations over {@link ScannerEntity}.
 */
@Service
public class ScannerPersistenceService {

    private static final Logger log = LoggerFactory.getLogger(ScannerPersistenceService.class);

    private final ScannerRepository scannerRepository;

    public ScannerPersistenceService(ScannerRepository scannerRepository) {
        this.scannerRepository = scannerRepository;
    }

    /**
     * Save a scanner entity (create or update).
     * New scanners are created with status="IDLE" by default.
     */
    public ScannerEntity save(String id, String targetDirectory) {
        ScannerEntity entity = scannerRepository.findById(id).orElseGet(ScannerEntity::new);
        entity.setId(id);
        entity.setTargetDirectory(targetDirectory);
        if (entity.getStatus() == null) {
            entity.setStatus("IDLE");
        }
        if (entity.getCreatedAt() == null) {
            entity.setCreatedAt(LocalDateTime.now());
        }
        return scannerRepository.save(entity);
    }

    /**
     * Save a scanner entity with full details.
     */
    public ScannerEntity save(ScannerInfo info) {
        ScannerEntity entity = scannerRepository.findById(info.id()).orElseGet(ScannerEntity::new);
        entity.setId(info.id());
        entity.setTargetDirectory(info.targetDirectory());
        entity.setStatus(info.status());
        entity.setCreatedAt(info.createdAt());
        entity.setLastEmittedAt(info.lastEmittedAt());
        return scannerRepository.save(entity);
    }

    /**
     * Find a scanner by its ID.
     */
    public Optional<ScannerEntity> findById(String id) {
        return scannerRepository.findById(id);
    }

    /**
     * Delete a scanner by its ID.
     */
    public void deleteById(String id) {
        scannerRepository.deleteById(id);
        log.info("Deleted scanner entity: {}", id);
    }

    /**
     * List all scanners.
     */
    public List<ScannerEntity> listAll() {
        return scannerRepository.findAll();
    }

    /**
     * Update the status of a scanner.
     */
    public void updateStatus(String id, String status) {
        scannerRepository.findById(id).ifPresent(entity -> {
            entity.setStatus(status);
            if ("EMITTING_UPDATES".equals(status)) {
                entity.setLastEmittedAt(LocalDateTime.now());
            }
            scannerRepository.save(entity);
            log.debug("Updated scanner {} status to {}", id, status);
        });
    }

    /**
     * Update the last emitted timestamp of a scanner.
     */
    public void updateLastEmittedAt(String id) {
        scannerRepository.findById(id).ifPresent(entity -> {
            entity.setLastEmittedAt(LocalDateTime.now());
            scannerRepository.save(entity);
        });
    }
}

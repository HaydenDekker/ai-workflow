package com.hdekker.ai_workflow.database.scanner;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * Entity for storing scanner state in the database.
 * Scanners are always created through agents (one-to-one relationship).
 * <p>
 * Persists to application.db table: scanner
 */
@Entity
@Table(name = "scanner")
public class ScannerEntity {

    @Id
    private String id;

    @Column(name = "target_directory", nullable = false)
    private String targetDirectory;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "IDLE";

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "last_emitted_at")
    private LocalDateTime lastEmittedAt;

    public ScannerEntity() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTargetDirectory() {
        return targetDirectory;
    }

    public void setTargetDirectory(String targetDirectory) {
        this.targetDirectory = targetDirectory;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getLastEmittedAt() {
        return lastEmittedAt;
    }

    public void setLastEmittedAt(LocalDateTime lastEmittedAt) {
        this.lastEmittedAt = lastEmittedAt;
    }
}

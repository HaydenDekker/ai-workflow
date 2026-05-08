package com.hdekker.ai_workflow.adapter.inbound.rest.dto;

import java.time.LocalDateTime;

/**
 * Lightweight, serialisable view of scanner state.
 * Scanners are first-class citizens: each owns exactly one agent's file-scanning task.
 *
 * @param id               unique scanner identifier
 * @param agentId          owner agent — never null after creation
 * @param targetDirectory  absolute path the scanner watches
 * @param status           current lifecycle state: IDLE, EMITTING_INITIAL, EMITTING_UPDATES, ERROR
 * @param createdAt        when the scanner was created
 * @param lastEmittedAt    timestamp of last file emission (null if none yet)
 * @param errorMessage     error message when status is ERROR (null otherwise)
 * @param fileCount        number of files in the watched folder (from metrics)
 * @param fileResult       file-level result: EMITTED, FILTERED, ERROR (from domain)
 */
public record ScannerInfoDTO(
    String id,
    String agentId,
    String targetDirectory,
    String status,
    LocalDateTime createdAt,
    LocalDateTime lastEmittedAt,
    String errorMessage,
    Long fileCount,
    String fileResult
) {
    /**
     * Backward-compatible constructor for tests.
     *
     * @param id              unique scanner identifier
     * @param agentId         owner agent
     * @param targetDirectory absolute path the scanner watches
     * @param status          current state
     * @param createdAt       when the scanner was created
     * @param lastEmittedAt   timestamp of last file emission
     */
    public ScannerInfoDTO(String id, String agentId, String targetDirectory,
                       String status, LocalDateTime createdAt, LocalDateTime lastEmittedAt) {
        this(id, agentId, targetDirectory, status, createdAt, lastEmittedAt, "", 0L, "");
    }

    /**
     * Canonical constructor body.
     */
    public ScannerInfoDTO {
        if (errorMessage == null) {
            errorMessage = "";
        }
    }
}

package com.hdekker.ai_workflow.rest.dto;

import java.time.LocalDateTime;

/**
 * Lightweight, serialisable view of scanner state.
 * Scanners are first-class citizens: each owns exactly one agent's file-scanning task.
 *
 * @param id               unique scanner identifier
 * @param agentId          owner agent — never null after creation
 * @param targetDirectory  absolute path the scanner watches
 * @param status           current state: IDLE, EMITTING_ALL, EMITTING_UPDATES, ERROR
 * @param createdAt        when the scanner was created
 * @param lastEmittedAt    timestamp of last file emission (null if none yet)
 */
public record ScannerInfo(
    String id,
    String agentId,
    String targetDirectory,
    String status,
    LocalDateTime createdAt,
    LocalDateTime lastEmittedAt
) {}

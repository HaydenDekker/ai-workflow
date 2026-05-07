package com.hdekker.ai_workflow.application.scanner.port;

import java.time.LocalDateTime;
import java.util.List;

import com.hdekker.ai_workflow.domain.scanner.ScannerEventType;
import com.hdekker.ai_workflow.domain.scanner.ScannerMetrics;
import com.hdekker.ai_workflow.domain.scanner.ScannerStatus;

/**
 * Port interface for scanner metrics observation.
 * <p>
 * Declares the metrics query and update contract the application layer
 * needs from the scanner observer. Infrastructure adapters (UI push,
 * REST polling) consume these metrics without the application layer
 * knowing about their implementation.
 */
public interface ScannerMetricsPort {

    /**
     * Record a scanner event for the given agent.
     * <p>
     * Updates internal counters based on the event type:
     * <ul>
     *   <li>CREATION / MODIFICATION — increment discovered count</li>
     *   <li>DELETION — no counter change</li>
     *   <li>UNCHANGED — no counter change</li>
     * </ul>
     *
     * @param agentId      the owning agent's ID
     * @param eventType    the type of file event
     * @param status       current scanner status
     * @param folderPath   the folder being scanned
     * @param errorMessage error message if status is ERROR
     * @param fileCount    the current file count in the watched folder
     */
    void recordEvent(String agentId, ScannerEventType eventType,
                     ScannerStatus status, String folderPath, String errorMessage,
                     long fileCount);

    /**
     * Record a scanner event for the given agent (file count defaults to 0).
     *
     * @param agentId      the owning agent's ID
     * @param eventType    the type of file event
     * @param status       current scanner status
     * @param folderPath   the folder being scanned
     * @param errorMessage error message if status is ERROR
     */
    default void recordEvent(String agentId, ScannerEventType eventType,
                             ScannerStatus status, String folderPath, String errorMessage) {
        recordEvent(agentId, eventType, status, folderPath, errorMessage, 0L);
    }

    /**
     * Record that a file was emitted, updating the last emission timestamp.
     *
     * @param agentId the owning agent's ID
     */
    void recordEmission(String agentId);

    /**
     * Push a status change to UI observers.
     * <p>
     * Used by the application layer to notify UI adapters of status transitions.
     *
     * @param agentId  the owning agent's ID
     * @param status   the new scanner status
     */
    void pushToUI(String agentId, ScannerStatus status);

    /**
     * Get a metrics snapshot for a specific agent.
     *
     * @param agentId the owning agent's ID
     * @return the agent's metrics, or a zeroed instance if not found
     */
    ScannerMetrics getMetrics(String agentId);

    /**
     * Get metrics snapshots for all registered agents.
     *
     * @return a list of all agent metric snapshots
     */
    List<ScannerMetrics> getAllMetrics();

    /**
     * Check whether the given agent's scanner is idle.
     * <p>
     * A scanner is idle if no emission has occurred for at least 30 seconds.
     *
     * @param agentId the owning agent's ID
     * @return true if the scanner is idle, false otherwise
     */
    boolean isIdle(String agentId);

    /**
     * Get the last emission timestamp for the given agent.
     *
     * @param agentId the owning agent's ID
     * @return the last emission timestamp, or null if none
     */
    LocalDateTime getLastEmissionTimestamp(String agentId);

}

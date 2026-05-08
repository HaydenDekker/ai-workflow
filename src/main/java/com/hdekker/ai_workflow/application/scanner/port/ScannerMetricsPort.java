package com.hdekker.ai_workflow.application.scanner.port;

import java.time.LocalDateTime;
import java.util.List;

import com.hdekker.ai_workflow.domain.scanner.ScannerEventType;
import com.hdekker.ai_workflow.domain.scanner.ScannerMetrics;

/**
 * Port interface for scanner metrics observation.
 * <p>
 * Pure metrics store — tracks file counts, discovered counts, and emission
 * timestamps. No push, no callbacks, no UI concerns.
 * <p>
 * Event publishing (push to UI) is handled by {@link ScannerEventPort}.
 */
public interface ScannerMetricsPort {

    /**
     * Record a scanner file event for the given agent.
     * <p>
     * Updates internal counters based on the event type:
     * <ul>
     *   <li>CREATION / MODIFICATION — increment discovered count</li>
     *   <li>DELETION — no counter change</li>
     *   <li>UNCHANGED — no counter change</li>
     * </ul>
     *
     * @param agentId   the owning agent's ID
     * @param eventType the type of file event
     * @param fileCount the current file count in the watched folder
     */
    void recordEvent(String agentId, ScannerEventType eventType, long fileCount);

    /**
     * Record a scanner event for the given agent (file count defaults to 0).
     *
     * @param agentId   the owning agent's ID
     * @param eventType the type of file event
     */
    default void recordEvent(String agentId, ScannerEventType eventType) {
        recordEvent(agentId, eventType, 0L);
    }

    /**
     * Record that a file was emitted, updating the last emission timestamp.
     *
     * @param agentId the owning agent's ID
     */
    void recordEmission(String agentId);

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

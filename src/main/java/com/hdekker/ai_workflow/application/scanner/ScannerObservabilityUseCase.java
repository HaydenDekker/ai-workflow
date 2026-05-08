package com.hdekker.ai_workflow.application.scanner;

import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hdekker.ai_workflow.application.scanner.port.ScannerEventPort;
import com.hdekker.ai_workflow.application.scanner.port.ScannerMetricsPort;
import com.hdekker.ai_workflow.domain.scanner.ScannerEventType;
import com.hdekker.ai_workflow.domain.scanner.ScannerFileResult;
import com.hdekker.ai_workflow.domain.scanner.ScannerMetrics;

import org.springframework.stereotype.Service;

/**
 * Orchestrator for scanner observability.
 * <p>
 * Single entry point that coordinates metrics recording and event publishing
 * as a unit. Replaces the implicit observability scattered across
 * {@link ScannerService}.
 * <p>
 * Each public method delegates to both {@link ScannerMetricsPort} (pure metrics
 * store) and {@link ScannerEventPort} (push-only event publishing) where
 * appropriate. Query-only methods delegate to the metrics port alone.
 * <p>
 * Hexagonal flow:
 * <pre>
 * ScannerService
 *   └─ observability.recordFileEvent(...)
 *        ├─ metrics.recordEvent(agentId, eventType, fileCount)
 *        └─ eventBus.publish(agentId, result, folderPath, errorMessage)
 * </pre>
 *
 * @see ScannerMetricsPort
 * @see ScannerEventPort
 * @see ScannerFileResult
 */
@Service
public class ScannerObservabilityUseCase {

    private static final Logger log = LoggerFactory.getLogger(ScannerObservabilityUseCase.class);

    private final ScannerMetricsPort metrics;
    private final ScannerEventPort eventBus;

    /**
     * Construct the use case with both ports injected.
     *
     * @param metrics  the metrics port for recording queries and stores
     * @param eventBus the event port for publishing file-level events
     */
    public ScannerObservabilityUseCase(ScannerMetricsPort metrics,
                                       ScannerEventPort eventBus) {
        this.metrics = metrics;
        this.eventBus = eventBus;
    }

    /**
     * Record a scanner file event.
     * <p>
     * Records the event with the metrics port and publishes the file-level
     * result through the event bus. The {@link ScannerFileResult} is derived
     * from the event type via {@link ScannerFileResult#from(ScannerEventType)}.
     *
     * @param agentId   the owning agent's ID
     * @param eventType the type of file event (nullable for lifecycle events)
     * @param fileCount the current file count in the watched folder
     * @param folderPath the folder being scanned (nullable)
     */
    public void recordFileEvent(String agentId, ScannerEventType eventType,
                                long fileCount, String folderPath) {
        ScannerFileResult result = ScannerFileResult.from(eventType);

        metrics.recordEvent(agentId, eventType, fileCount);
        eventBus.publish(agentId, result, folderPath, null);

        log.debug("Recorded file event for agent {}: type={}, result={}, count={}, folder={}",
                agentId, eventType, result, fileCount, folderPath);
    }

    /**
     * Record that a file was emitted, updating the emission timestamp.
     * <p>
     * Records the emission with the metrics port and publishes an EMITTED
     * event through the event bus.
     *
     * @param agentId    the owning agent's ID
     * @param folderPath the folder being scanned (nullable)
     */
    public void recordEmission(String agentId, String folderPath) {
        metrics.recordEmission(agentId);
        eventBus.publish(agentId, ScannerFileResult.EMITTED, folderPath, null);

        log.debug("Recorded emission for agent {}", agentId);
    }

    /**
     * Get a metrics snapshot for a specific agent.
     * <p>
     * Delegates to the metrics port only — no event publishing.
     *
     * @param agentId the owning agent's ID
     * @return the agent's metrics, or a zeroed instance if not found
     */
    public ScannerMetrics getMetrics(String agentId) {
        return metrics.getMetrics(agentId);
    }

    /**
     * Get metrics snapshots for all registered agents.
     * <p>
     * Delegates to the metrics port only — no event publishing.
     *
     * @return a list of all agent metric snapshots
     */
    public List<ScannerMetrics> getAllMetrics() {
        return metrics.getAllMetrics();
    }

    /**
     * Check whether the given agent's scanner is idle.
     * <p>
     * Delegates to the metrics port only — no event publishing.
     *
     * @param agentId the owning agent's ID
     * @return true if the scanner is idle, false otherwise
     */
    public boolean isIdle(String agentId) {
        return metrics.isIdle(agentId);
    }

    /**
     * Get the last emission timestamp for the given agent.
     * <p>
     * Delegates to the metrics port only — no event publishing.
     *
     * @param agentId the owning agent's ID
     * @return the last emission timestamp, or null if none
     */
    public LocalDateTime getLastEmissionTimestamp(String agentId) {
        return metrics.getLastEmissionTimestamp(agentId);
    }

    /**
     * Transition the scanner to the ERROR state.
     * <p>
     * Records a lifecycle event with the metrics port (null event type, zero
     * file count) and publishes an ERROR event through the event bus with the
     * given error message.
     *
     * @param agentId  the owning agent's ID
     * @param message  the error message (nullable)
     */
    public void transitionToError(String agentId, String message) {
        metrics.recordEvent(agentId, null, 0L);
        eventBus.publish(agentId, ScannerFileResult.ERROR, null, message);

        log.error("Scanner for agent {} entered ERROR state: {}", agentId, message);
    }
}

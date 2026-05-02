package com.hdekker.ai_workflow.domain.scanner;

import java.time.LocalDateTime;

/**
 * Pure domain model for per-agent scanner metrics.
 * <p>
 * Carries discovery and emission counters independent of any infrastructure or UI concerns.
 * Computed on-demand by the application layer; the UI and REST adapters convert this
 * to their own DTO representations.
 */
public record ScannerMetrics(
        String agentId,
        long totalDiscovered,
        LocalDateTime lastEmissionTimestamp
) {

    /**
     * Returns a new instance with the discovered count incremented by one.
     */
    public ScannerMetrics withDiscovered() {
        return new ScannerMetrics(agentId, totalDiscovered + 1, lastEmissionTimestamp);
    }

    /**
     * Returns a new instance with the last emission timestamp updated.
     */
    public ScannerMetrics withLastEmission(LocalDateTime timestamp) {
        return new ScannerMetrics(agentId, totalDiscovered, timestamp);
    }

    /**
     * Returns a zeroed metrics instance for a new agent.
     */
    public static ScannerMetrics empty(String agentId) {
        return new ScannerMetrics(agentId, 0, null);
    }
}

package com.hdekker.ai_workflow.rest.dto;

/**
 * Snapshot of scanner metrics for a specific agent.
 * <p>
 * Used by the UI layer to display real-time scanner statistics.
 *
 * @param agentId        the owning agent's ID
 * @param fileCount      current files in target directory
 * @param totalDiscovered   files found since scanner started
 * @param unchanged        files matching previous hash (skipped)
 */
public record ScannerMetricsSnapshot(
        String agentId,
        long fileCount,        // current files in target directory
        long totalDiscovered,  // files found since scanner started
        long unchanged         // files matching previous hash (skipped)
) {}

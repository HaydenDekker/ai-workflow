package com.hdekker.ai_workflow.rest.dto;

/**
 * Snapshot of scanner metrics for a specific agent.
 * <p>
 * Used by the UI layer to display real-time scanner statistics.
 * The file count is computed on-demand by walking the watched directory.
 *
 * @param agentId         the owning agent's ID
 * @param fileCount       current files in target directory
 * @param totalDiscovered files discovered (created or modified) since scanner started
 */
public record ScannerMetricsSnapshot(
        String agentId,
        long fileCount,
        long totalDiscovered
) {}

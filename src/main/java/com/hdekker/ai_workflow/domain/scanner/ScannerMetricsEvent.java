package com.hdekker.ai_workflow.domain.scanner;

/**
 * Domain event fired when scanner metrics change.
 * <p>
 * Replaces the former UI-scoped {@code ScannerMetricsChangedEvent}.
 * The application layer publishes this event; UI and REST adapters
 * subscribe to it and convert to their own representations.
 * <p>
 * Carries both {@link ScannerStatus} (for state display) and
 * {@link ScannerEventType} (for metrics logic). The {@code eventType}
 * is nullable — populated for file events (CREATION, MODIFICATION,
 * DELETION, UNCHANGED), null for lifecycle events (emission, error, recovery).
 *
 * @param agentId        the owning agent's ID
 * @param status         the current scanner status
 * @param eventType      the type of file event (nullable)
 * @param folderPath     the folder being scanned (nullable)
 * @param errorMessage   error message if status is ERROR (nullable)
 */
public record ScannerMetricsEvent(
        String agentId,
        ScannerStatus status,
        ScannerEventType eventType,
        String folderPath,
        String errorMessage,
        long fileCount
) {

    /**
     * Create a file discovery event.
     */
    public static ScannerMetricsEvent creation(String agentId, String folderPath) {
        return new ScannerMetricsEvent(agentId, ScannerStatus.EMITTING_UPDATES,
                ScannerEventType.CREATION, folderPath, null, 0);
    }

    /**
     * Create a file modification event.
     */
    public static ScannerMetricsEvent modification(String agentId, String folderPath) {
        return new ScannerMetricsEvent(agentId, ScannerStatus.EMITTING_UPDATES,
                ScannerEventType.MODIFICATION, folderPath, null, 0);
    }

    /**
     * Create a file deletion event.
     */
    public static ScannerMetricsEvent deletion(String agentId, String folderPath) {
        return new ScannerMetricsEvent(agentId, ScannerStatus.EMITTING_UPDATES,
                ScannerEventType.DELETION, folderPath, null, 0);
    }

    /**
     * Create an unchanged file event.
     */
    public static ScannerMetricsEvent unchanged(String agentId, String folderPath) {
        return new ScannerMetricsEvent(agentId, ScannerStatus.FILTERED,
                ScannerEventType.UNCHANGED, folderPath, null, 0);
    }

    /**
     * Create a status transition event (emission, error, recovery).
     */
    public static ScannerMetricsEvent status(String agentId, ScannerStatus status, String errorMessage) {
        return new ScannerMetricsEvent(agentId, status, null, null, errorMessage, 0);
    }

    /**
     * Create a file discovery event with a file count.
     */
    public static ScannerMetricsEvent creation(String agentId, String folderPath, long fileCount) {
        return new ScannerMetricsEvent(agentId, ScannerStatus.EMITTING_UPDATES,
                ScannerEventType.CREATION, folderPath, null, fileCount);
    }

    /**
     * Create a file modification event with a file count.
     */
    public static ScannerMetricsEvent modification(String agentId, String folderPath, long fileCount) {
        return new ScannerMetricsEvent(agentId, ScannerStatus.EMITTING_UPDATES,
                ScannerEventType.MODIFICATION, folderPath, null, fileCount);
    }

    /**
     * Create a file deletion event with a file count.
     */
    public static ScannerMetricsEvent deletion(String agentId, String folderPath, long fileCount) {
        return new ScannerMetricsEvent(agentId, ScannerStatus.EMITTING_UPDATES,
                ScannerEventType.DELETION, folderPath, null, fileCount);
    }

    /**
     * Create an unchanged file event with a file count.
     */
    public static ScannerMetricsEvent unchanged(String agentId, String folderPath, long fileCount) {
        return new ScannerMetricsEvent(agentId, ScannerStatus.FILTERED,
                ScannerEventType.UNCHANGED, folderPath, null, fileCount);
    }

    /**
     * Create a status transition event with a file count.
     */
    public static ScannerMetricsEvent status(String agentId, ScannerStatus status, String errorMessage, long fileCount) {
        return new ScannerMetricsEvent(agentId, status, null, null, errorMessage, fileCount);
    }
}

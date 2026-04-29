package com.hdekker.ai_workflow.ui.events;

/**
 * Fired when scanner metrics change (file discovered, file count updated, etc).
 * Used to push real-time updates to the ScannerListView UI.
 * <p>
 * Event types: "discovered", "unchanged", "file_count", "error", "recovered", "idle".
 */
public class ScannerMetricsChangedEvent {

    private final String agentId;
    private final String type;  // "discovered", "unchanged", "file_count", "error", "recovered", "idle"
    private final String errorMessage;  // non-null only when type is "error"

    public ScannerMetricsChangedEvent(String agentId, String type) {
        this(agentId, type, null);
    }

    public ScannerMetricsChangedEvent(String agentId, String type, String errorMessage) {
        this.agentId = agentId;
        this.type = type;
        this.errorMessage = errorMessage;
    }

    public String getAgentId() {
        return agentId;
    }

    public String getType() {
        return type;
    }

    /**
     * Get the error message, if this event represents an error state.
     */
    public String getErrorMessage() {
        return errorMessage;
    }

    /**
     * Create an event for a file discovery.
     */
    public static ScannerMetricsChangedEvent fileDiscovered(String agentId) {
        return new ScannerMetricsChangedEvent(agentId, "discovered");
    }

    /**
     * Create an event for an unchanged file.
     */
    public static ScannerMetricsChangedEvent fileUnchanged(String agentId) {
        return new ScannerMetricsChangedEvent(agentId, "unchanged");
    }

    /**
     * Create an event for a file count gauge update.
     */
    public static ScannerMetricsChangedEvent fileCountUpdated(String agentId) {
        return new ScannerMetricsChangedEvent(agentId, "file_count");
    }

    /**
     * Create an event for a file emission (file actually sent through the sink).
     * Triggers UI refresh so the status column shows EMITTING_UPDATES.
     */
    public static ScannerMetricsChangedEvent fileEmitted(String agentId) {
        return new ScannerMetricsChangedEvent(agentId, "emitted");
    }

    /**
     * Create an event for a scanner error.
     */
    public static ScannerMetricsChangedEvent errorOccurred(String agentId, String message) {
        return new ScannerMetricsChangedEvent(agentId, "error", message);
    }

    /**
     * Create an event for a scanner recovery from error.
     */
    public static ScannerMetricsChangedEvent recoveredFromError(String agentId) {
        return new ScannerMetricsChangedEvent(agentId, "recovered");
    }

    /**
     * Create an event for a scanner becoming idle.
     */
    public static ScannerMetricsChangedEvent idleReached(String agentId) {
        return new ScannerMetricsChangedEvent(agentId, "idle");
    }

    /**
     * Create an event for a scanner status change (e.g. IDLE -> EMITTING_UPDATES).
     *
     * @param agentId the owning agent's ID
     * @param status  the new status string
     */
    public static ScannerMetricsChangedEvent statusChanged(String agentId, String status) {
        return new ScannerMetricsChangedEvent(agentId, "status_change", status);
    }

    /**
     * Create an event for any scanner metric change (used when agentId is not yet known).
     */
    public static ScannerMetricsChangedEvent scannerMetricsChanged(String type) {
        return new ScannerMetricsChangedEvent(null, type);
    }
}

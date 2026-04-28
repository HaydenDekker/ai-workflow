package com.hdekker.ai_workflow.ui.events;

/**
 * Fired when scanner metrics change (file discovered, file count updated, etc).
 * Used to push real-time updates to the ScannerListView UI.
 */
public class ScannerMetricsChangedEvent {

    private final String agentId;
    private final String type;  // "discovered", "unchanged", "file_count"

    public ScannerMetricsChangedEvent(String agentId, String type) {
        this.agentId = agentId;
        this.type = type;
    }

    public String getAgentId() {
        return agentId;
    }

    public String getType() {
        return type;
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
     * Create an event for any scanner metric change (used when agentId is not yet known).
     */
    public static ScannerMetricsChangedEvent scannerMetricsChanged(String type) {
        return new ScannerMetricsChangedEvent(null, type);
    }
}

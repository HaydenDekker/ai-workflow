package com.hdekker.ai_workflow.ui.events;

import com.hdekker.ai_workflow.usecases.ScannerEventType;
import com.hdekker.ai_workflow.usecases.ScannerStatus;

/**
 * Fired when scanner metrics change (file discovered, file count updated, etc).
 * Used to push real-time updates to the ScannerListView UI.
 * <p>
 * Carries both {@link ScannerStatus} (for UI state) and {@link ScannerEventType} (for metrics logic).
 * The {@code eventType} is nullable — populated for file events (CREATION, MODIFICATION, DELETION, UNCHANGED),
 * null for lifecycle events (emission, error, recovery).
 */
public class ScannerMetricsChangedEvent {

    private final String agentId;
    private final ScannerStatus status;
    private final ScannerEventType eventType;  // nullable — present for file events
    private final String folderPath;           // nullable — present for file events
    private final String errorMessage;         // non-null only when status is ERROR

    public ScannerMetricsChangedEvent(String agentId, ScannerStatus status, ScannerEventType eventType, String folderPath, String errorMessage) {
        this.agentId = agentId;
        this.status = status;
        this.eventType = eventType;
        this.folderPath = folderPath;
        this.errorMessage = errorMessage;
    }

    public String getAgentId() {
        return agentId;
    }

    /**
     * Get the scanner status for UI state display.
     */
    public ScannerStatus getStatus() {
        return status;
    }

    /**
     * Get the event type for metrics logic.
     * Nullable — populated for file events (CREATION, MODIFICATION, DELETION, UNCHANGED),
     * null for lifecycle events (emission, error, recovery).
     */
    public ScannerEventType getEventType() {
        return eventType;
    }

    /**
     * Get the folder path, if this event relates to a file event.
     */
    public String getFolderPath() {
        return folderPath;
    }

    /**
     * Get the error message, if this event represents an error state.
     */
    public String getErrorMessage() {
        return errorMessage;
    }

    /**
     * Get the event type string for backward compatibility.
     * Returns the event type name when present, otherwise the status name.
     */
    public String getType() {
        if (eventType != null) {
            return eventType.name().toLowerCase();
        }
        return status.name().toLowerCase();
    }
}

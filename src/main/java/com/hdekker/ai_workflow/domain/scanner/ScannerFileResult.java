package com.hdekker.ai_workflow.domain.scanner;

/**
 * File-level result of a single scanner file event.
 * <p>
 * This enum represents what happened to an individual file during scanning —
 * not the overall scanner lifecycle state. Use {@link ScannerStatus} for
 * scanner-level states (EMITTING_INITIAL, IDLE, etc.). Use this enum for
 * per-file outcomes.
 * <p>
 * {@code EMITTED} — the file was sent to the pipeline (creation, modification,
 * or deletion).
 * {@code FILTERED} — the file was rejected by the hash filter (unchanged).
 * {@code ERROR} — the scanner encountered an error processing the file.
 *
 * @see ScannerStatus
 * @see ScannerEventType
 */
public enum ScannerFileResult {

    /** The file was emitted to the processing pipeline. */
    EMITTED,

    /** The file was filtered out (unchanged / already known). */
    FILTERED,

    /** The scanner encountered an error while processing the file. */
    ERROR;

    /**
     * Map a {@link ScannerEventType} to the corresponding file result.
     * <p>
     * {@code CREATION} and {@code MODIFICATION} produce {@link #EMITTED} because
     * the file is new or changed and should be processed.
     * {@code DELETION} produces {@link #EMITTED} because deletions are also
     * emitted to the pipeline for downstream handling.
     * {@code UNCHANGED} produces {@link #FILTERED} because the file hash matched
     * a known version and was rejected by the filter.
     * {@code null} (lifecycle events) defaults to {@link #EMITTED}.
     *
     * @param eventType the scanner event type, or {@code null} for lifecycle events
     * @return the file result for this event type
     */
    public static ScannerFileResult from(ScannerEventType eventType) {
        if (eventType == null) {
            return EMITTED;
        }

        return switch (eventType) {
            case CREATION, MODIFICATION, DELETION -> EMITTED;
            case UNCHANGED -> FILTERED;
        };
    }

    /**
     * Parse a result name (case-insensitive) into the corresponding enum value.
     *
     * @param name the result name (e.g. "EMITTED", "filtered")
     * @return the matching enum value
     * @throws IllegalArgumentException if the name is blank or has no matching constant
     */
    public static ScannerFileResult fromValue(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Result name must not be blank");
        }
        return valueOf(name.trim().toUpperCase());
    }
}

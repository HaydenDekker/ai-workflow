package com.hdekker.ai_workflow.domain.scanner;

/**
 * Scanner status enum — replaces the string constants for type-safe status handling.
 * <p>
 * The {@code name()} of each constant matches the legacy string value so that
 * conversion to/from DTOs and database entities is straightforward.
 */
public enum ScannerStatus {

    /** Performing a full scan of existing files. */
    EMITTING_INITIAL,

    /** Watching for incremental changes. */
    EMITTING_UPDATES,

    /** No event for 30 seconds, idle watching. */
    IDLE,

    /** Hash filter rejected a file (unchanged / already known). */
    FILTERED,

    /** Scanner encountered an error. */
    ERROR;

    /**
     * Parse a status name (case-insensitive) into the corresponding enum value.
     *
     * @param name the status name (e.g. "IDLE", "error")
     * @return the matching enum value
     * @throws IllegalArgumentException if no matching constant exists
     */
    public static ScannerStatus fromName(String name) {
        return valueOf(name.trim().toUpperCase());
    }
}

package com.hdekker.ai_workflow.domain.scanner;

/**
 * Domain event carrying file-level scanner results.
 * <p>
 * Published by {@link com.hdekker.ai_workflow.application.scanner.port.ScannerEventPort}
 * when a file event is processed. Carries the result outcome, agent context,
 * and optional error details — independent of scanner lifecycle status.
 *
 * @param agentId      the owning agent's ID
 * @param result       the file-level result (EMITTED, FILTERED, ERROR)
 * @param folderPath   the folder being scanned (nullable)
 * @param errorMessage error message when result is ERROR (nullable)
 */
public record ScannerFileEvent(
        String agentId,
        ScannerFileResult result,
        String folderPath,
        String errorMessage
) {

    /**
     * Create an emitted file event.
     *
     * @param agentId    the owning agent's ID
     * @param folderPath the folder being scanned
     * @return a new emitted event
     */
    public static ScannerFileEvent emitted(String agentId, String folderPath) {
        return new ScannerFileEvent(agentId, ScannerFileResult.EMITTED, folderPath, null);
    }

    /**
     * Create a filtered file event.
     *
     * @param agentId    the owning agent's ID
     * @param folderPath the folder being scanned
     * @return a new filtered event
     */
    public static ScannerFileEvent filtered(String agentId, String folderPath) {
        return new ScannerFileEvent(agentId, ScannerFileResult.FILTERED, folderPath, null);
    }

    /**
     * Create an error file event.
     *
     * @param agentId      the owning agent's ID
     * @param errorMessage the error message
     * @return a new error event
     */
    public static ScannerFileEvent error(String agentId, String errorMessage) {
        return new ScannerFileEvent(agentId, ScannerFileResult.ERROR, null, errorMessage);
    }
}

package com.hdekker.ai_workflow.application.scanner.port;

import java.util.function.Consumer;

import com.hdekker.ai_workflow.domain.scanner.ScannerFileEvent;
import com.hdekker.ai_workflow.domain.scanner.ScannerFileResult;

/**
 * Port interface for scanner event publishing.
 * <p>
 * Push-only interface — receives file-level events and distributes them
 * to registered callbacks (UI push, logging, etc.). Separated from
 * {@link ScannerMetricsPort} to isolate the push mechanism from the
 * metrics store.
 *
 * @see ScannerFileEvent
 * @see ScannerFileResult
 */
public interface ScannerEventPort {

    /**
     * Publish a file-level event for the given agent.
     * <p>
     * Notifies all registered callbacks with the event details.
     *
     * @param agentId      the owning agent's ID
     * @param result       the file-level result (EMITTED, FILTERED, ERROR)
     * @param folderPath   the folder being scanned (nullable)
     * @param errorMessage error message when result is ERROR (nullable)
     */
    void publish(String agentId, ScannerFileResult result,
                 String folderPath, String errorMessage);

    /**
     * Register a callback to receive published events.
     * <p>
     * Callbacks are invoked synchronously on the calling thread.
     * Use thread-safe collections if callbacks run concurrently.
     *
     * @param callback the consumer to invoke on each published event
     */
    void registerCallback(Consumer<ScannerFileEvent> callback);

    /**
     * Unregister a previously registered callback.
     *
     * @param callback the consumer to remove
     */
    void unregisterCallback(Consumer<ScannerFileEvent> callback);
}

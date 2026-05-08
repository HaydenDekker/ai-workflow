package com.hdekker.ai_workflow.application.scanner;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hdekker.ai_workflow.application.scanner.port.ScannerEventPort;
import com.hdekker.ai_workflow.domain.scanner.ScannerFileEvent;
import com.hdekker.ai_workflow.domain.scanner.ScannerFileResult;

import org.springframework.stereotype.Service;

/**
 * Event bus for scanner file-level events.
 * <p>
 * Implements {@link ScannerEventPort} — the push-only interface for
 * distributing file-level scanner events to registered callbacks
 * (UI push, logging, etc.). Separated from {@link ScannerMetricsService}
 * to isolate the push mechanism from the metrics store.
 * <p>
 * Callbacks are invoked synchronously on the calling thread. Individual
 * callback failures are caught and logged so they do not break other
 * callbacks.
 *
 * @see ScannerEventPort
 * @see ScannerFileEvent
 */
@Service
public class ScannerEventBus implements ScannerEventPort {

    private static final Logger log = LoggerFactory.getLogger(ScannerEventBus.class);

    /**
     * Thread-safe list of registered callbacks.
     */
    private final List<Consumer<ScannerFileEvent>> callbacks = new CopyOnWriteArrayList<>();

    @Override
    public void publish(String agentId, ScannerFileResult result,
                        String folderPath, String errorMessage) {
        ScannerFileEvent event = new ScannerFileEvent(agentId, result, folderPath, errorMessage);
        for (Consumer<ScannerFileEvent> callback : callbacks) {
            try {
                callback.accept(event);
            } catch (Exception e) {
                log.warn("Error in event callback for agent {}: {}",
                        agentId, e.getMessage());
            }
        }
    }

    @Override
    public void registerCallback(Consumer<ScannerFileEvent> callback) {
        callbacks.add(callback);
        log.debug("Scanner event callback registered");
    }

    @Override
    public void unregisterCallback(Consumer<ScannerFileEvent> callback) {
        callbacks.remove(callback);
        log.debug("Scanner event callback unregistered");
    }
}

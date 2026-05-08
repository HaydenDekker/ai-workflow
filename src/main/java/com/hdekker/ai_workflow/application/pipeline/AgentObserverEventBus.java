package com.hdekker.ai_workflow.application.pipeline;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.hdekker.ai_workflow.application.pipeline.port.AgentObserverEventPort;
import com.hdekker.ai_workflow.domain.pipeline.AgentObserverEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Event bus for agent observer domain events.
 * <p>
 * Implements {@link AgentObserverEventPort} — the push-only interface for
 * distributing agent observer events to registered callbacks
 * (UI push, logging, dashboards, etc.). Separated from
 * {@link AgentObserverService} to isolate the push mechanism from the
 * metrics store.
 * <p>
 * Callbacks are invoked synchronously on the calling thread. Individual
 * callback failures are caught and logged so they do not break other
 * callbacks.
 *
 * @see AgentObserverEventPort
 * @see AgentObserverEvent
 * @see AgentObserverService
 */
@Service
public class AgentObserverEventBus implements AgentObserverEventPort {

    private static final Logger log = LoggerFactory.getLogger(AgentObserverEventBus.class);

    /**
     * Thread-safe list of registered callbacks.
     */
    private final List<java.util.function.Consumer<AgentObserverEvent>> callbacks
            = new CopyOnWriteArrayList<>();

    @Override
    public void registerCallback(
            java.util.function.Consumer<AgentObserverEvent> callback) {
        callbacks.add(callback);
        log.debug("Agent observer event callback registered");
    }

    @Override
    public void unregisterCallback(
            java.util.function.Consumer<AgentObserverEvent> callback) {
        callbacks.remove(callback);
        log.debug("Agent observer event callback unregistered");
    }

    /**
     * Publish an agent observer event to all registered callbacks.
     * <p>
     * Each callback is invoked in its own try-catch block so that a failure
     * in one callback does not prevent subsequent callbacks from receiving
     * the event.
     *
     * @param event the agent observer event to publish
     */
    public void publish(AgentObserverEvent event) {
        for (java.util.function.Consumer<AgentObserverEvent> callback : callbacks) {
            try {
                callback.accept(event);
            } catch (Exception e) {
                log.error("Error in event callback for agent {}: {}",
                        event.agentId(), e.getMessage());
            }
        }
    }
}

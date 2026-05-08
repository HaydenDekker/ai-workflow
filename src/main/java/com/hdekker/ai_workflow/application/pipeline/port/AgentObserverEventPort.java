package com.hdekker.ai_workflow.application.pipeline.port;

import java.util.function.Consumer;

import com.hdekker.ai_workflow.domain.pipeline.AgentObserverEvent;

/**
 * Port interface for agent observer event publishing.
 * <p>
 * Push-only interface — receives domain events and distributes them
 * to registered callbacks (UI push, logging, dashboards, etc.).
 * Separated from {@link AgentObserverPort} to isolate the push mechanism
 * from the metrics store.
 *
 * @see AgentObserverEvent
 * @see AgentObserverPort
 */
public interface AgentObserverEventPort {

    /**
     * Register a callback to receive published events.
     * <p>
     * Callbacks are invoked synchronously on the calling thread.
     * Use thread-safe collections if callbacks run concurrently.
     *
     * @param callback the consumer to invoke on each published event
     */
    void registerCallback(Consumer<AgentObserverEvent> callback);

    /**
     * Unregister a previously registered callback.
     *
     * @param callback the consumer to remove
     */
    void unregisterCallback(Consumer<AgentObserverEvent> callback);

    /**
     * Publish an agent observer event to all registered callbacks.
     *
     * @param event the agent observer event to publish
     */
    void publish(AgentObserverEvent event);
}

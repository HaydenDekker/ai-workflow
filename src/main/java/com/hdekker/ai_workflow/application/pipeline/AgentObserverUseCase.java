package com.hdekker.ai_workflow.application.pipeline;

import java.nio.file.Path;
import java.time.LocalDateTime;

import com.hdekker.ai_workflow.application.pipeline.port.AgentObserverEventPort;
import com.hdekker.ai_workflow.application.pipeline.port.AgentObserverPort;
import com.hdekker.ai_workflow.domain.pipeline.AgentObserverEvent;
import com.hdekker.ai_workflow.domain.pipeline.AgentObserverEventType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Orchestrator for agent observer metrics and event publishing.
 * <p>
 * Single entry point that coordinates metrics recording and event publishing
 * as a unit. Replaces the implicit observability scattered across
 * the agent pipeline stages.
 * <p>
 * Each public method delegates to both {@link AgentObserverPort} (pure metrics
 * store) and {@link AgentObserverEventPort} (push-only event publishing) where
 * appropriate. Query-only methods delegate to the metrics port alone.
 * <p>
 * Hexagonal flow:
 * <pre>
 * AgentConfigurator (wires into pipeline)
 *   └─ doOnNext(response) → recordDispatch(agentId, fileName)
 *        ├─ metrics.recordDispatch(agentId, fileName)
 *        └─ eventBus.publish(new AgentObserverEvent(agentId, DISPATCHED, ...))
 *   └─ persister wrapper → recordStorage(agentId, outputName, path)
 *        ├─ metrics.recordStorage(agentId, outputName, path)
 *        └─ eventBus.publish(new AgentObserverEvent(agentId, STORED, ...))
 * </pre>
 *
 * @see AgentObserverPort
 * @see AgentObserverEventPort
 * @see AgentObserverService
 * @see AgentObserverEventBus
 */
@Service
public class AgentObserverUseCase {

    private static final Logger log = LoggerFactory.getLogger(AgentObserverUseCase.class);

    private final AgentObserverPort metrics;
    private final AgentObserverEventPort eventBus;

    /**
     * Construct the use case with both ports injected.
     *
     * @param metrics  the metrics port for recording dispatches and storages
     * @param eventBus the event port for publishing observer events
     */
    public AgentObserverUseCase(AgentObserverPort metrics,
                                AgentObserverEventPort eventBus) {
        this.metrics = metrics;
        this.eventBus = eventBus;
    }

    /**
     * Record that a prompt was dispatched to the LLM.
     * <p>
     * Records the dispatch with the metrics port and publishes a
     * {@link AgentObserverEventType#DISPATCHED} event through the event bus.
     *
     * @param agentId  the owning agent's ID
     * @param fileName the file name being dispatched
     */
    public void recordDispatch(String agentId, String fileName) {
        metrics.recordDispatch(agentId, fileName);
        eventBus.publish(AgentObserverEvent.dispatched(agentId, fileName));

        log.debug("Recorded dispatch for agent {}: file={}", agentId, fileName);
    }

    /**
     * Record that a response was stored to the output directory.
     * <p>
     * Records the storage with the metrics port and publishes a
     * {@link AgentObserverEventType#STORED} event through the event bus.
     *
     * @param agentId    the owning agent's ID
     * @param outputName the name of the stored output file
     * @param outputPath the full path where the output was stored (nullable)
     */
    public void recordStorage(String agentId, String outputName, Path outputPath) {
        metrics.recordStorage(agentId, outputName, outputPath);
        eventBus.publish(AgentObserverEvent.stored(agentId, outputName));

        log.debug("Recorded storage for agent {}: output={}", agentId, outputName);
    }

    /**
     * Get the dispatch count for a specific agent.
     * <p>
     * Delegates to the metrics port only — no event publishing.
     *
     * @param agentId the owning agent's ID
     * @return the number of dispatches recorded for this agent
     */
    public long getDispatchCount(String agentId) {
        return metrics.getDispatchCount(agentId);
    }

    /**
     * Get the total dispatch count across all agents.
     * <p>
     * Delegates to the metrics port only — no event publishing.
     *
     * @return the sum of dispatches across all agents
     */
    public long getTotalDispatchCount() {
        return metrics.getTotalDispatchCount();
    }

    /**
     * Get the storage count for a specific agent.
     * <p>
     * Delegates to the metrics port only — no event publishing.
     *
     * @param agentId the owning agent's ID
     * @return the number of storage events recorded for this agent
     */
    public long getStorageCount(String agentId) {
        return metrics.getStorageCount(agentId);
    }

    /**
     * Get the total storage count across all agents.
     * <p>
     * Delegates to the metrics port only — no event publishing.
     *
     * @return the sum of storage events across all agents
     */
    public long getTotalStorageCount() {
        return metrics.getTotalStorageCount();
    }
}

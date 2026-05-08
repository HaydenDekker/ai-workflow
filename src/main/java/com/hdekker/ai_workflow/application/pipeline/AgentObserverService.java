package com.hdekker.ai_workflow.application.pipeline;

import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;

import com.hdekker.ai_workflow.application.pipeline.port.AgentObserverPort;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Central service for agent observer metrics.
 * <p>
 * Implements {@link AgentObserverPort} — the application-layer port
 * for agent observer metrics. Tracks per-agent dispatch counts and storage
 * counts. Thread-safe via {@link ConcurrentHashMap}.
 * <p>
 * Pure metrics store — no push, no callbacks, no UI concerns.
 * Event publishing is handled by {@link AgentObserverEventBus}.
 *
 * @see AgentObserverPort
 * @see AgentObserverEventBus
 */
@Service
public class AgentObserverService implements AgentObserverPort {

    private static final Logger log = LoggerFactory.getLogger(AgentObserverService.class);

    /**
     * Thread-safe store of per-agent dispatch counters.
     */
    private final ConcurrentHashMap<String, Long> dispatchCounters = new ConcurrentHashMap<>();

    /**
     * Thread-safe store of per-agent storage counters.
     */
    private final ConcurrentHashMap<String, Long> storageCounters = new ConcurrentHashMap<>();

    /**
     * Construct the service — no external dependencies needed.
     */
    public AgentObserverService() {
    }

    /**
     * Record that a prompt was dispatched to the LLM for the given agent.
     * <p>
     * Atomically increments the dispatch counter for the agent using
     * {@link ConcurrentHashMap#merge(Object, Object, java.util.function.BiFunction)}.
     *
     * @param agentId  the owning agent's ID
     * @param fileName the file name being dispatched (logged for debugging)
     */
    @Override
    public void recordDispatch(String agentId, String fileName) {
        dispatchCounters.merge(agentId, 1L, Long::sum);
        log.debug("Recorded dispatch for agent {}: file={}", agentId, fileName);
    }

    /**
     * Record that a response was stored to the output directory for the given agent.
     * <p>
     * Atomically increments the storage counter for the agent using
     * {@link ConcurrentHashMap#merge(Object, Object, java.util.function.BiFunction)}.
     *
     * @param agentId     the owning agent's ID
     * @param outputName  the name of the stored output file (logged for debugging)
     * @param outputPath  the full path where the output was stored (nullable, unused)
     */
    @Override
    public void recordStorage(String agentId, String outputName, Path outputPath) {
        storageCounters.merge(agentId, 1L, Long::sum);
        log.debug("Recorded storage for agent {}: output={}", agentId, outputName);
    }

    /**
     * Get the dispatch count for a specific agent.
     *
     * @param agentId the owning agent's ID
     * @return the number of dispatches recorded for this agent, or 0 if none
     */
    @Override
    public long getDispatchCount(String agentId) {
        return dispatchCounters.getOrDefault(agentId, 0L);
    }

    /**
     * Get the total dispatch count across all agents.
     *
     * @return the sum of dispatches across all agents
     */
    @Override
    public long getTotalDispatchCount() {
        return dispatchCounters.values().stream().mapToLong(Long::longValue).sum();
    }

    /**
     * Get the storage count for a specific agent.
     *
     * @param agentId the owning agent's ID
     * @return the number of storage events recorded for this agent, or 0 if none
     */
    @Override
    public long getStorageCount(String agentId) {
        return storageCounters.getOrDefault(agentId, 0L);
    }

    /**
     * Get the total storage count across all agents.
     *
     * @return the sum of storage events across all agents
     */
    @Override
    public long getTotalStorageCount() {
        return storageCounters.values().stream().mapToLong(Long::longValue).sum();
    }
}

package com.hdekker.ai_workflow.application.pipeline.port;

import java.nio.file.Path;

/**
 * Port interface for agent observer metrics recording.
 * <p>
 * Pure metrics store — tracks dispatch counts and storage counts per agent.
 * No push, no callbacks, no UI concerns.
 * <p>
 * Event publishing (push to subscribers) is handled by
 * {@link AgentObserverEventPort}.
 */
public interface AgentObserverPort {

    /**
     * Record that a prompt was dispatched to the LLM for the given agent.
     *
     * @param agentId  the owning agent's ID
     * @param fileName the file name being dispatched
     */
    void recordDispatch(String agentId, String fileName);

    /**
     * Record that a response was stored to the output directory for the given agent.
     *
     * @param agentId     the owning agent's ID
     * @param outputName  the name of the stored output file
     * @param outputPath  the full path where the output was stored (nullable)
     */
    void recordStorage(String agentId, String outputName, Path outputPath);

    /**
     * Get the dispatch count for a specific agent.
     *
     * @param agentId the owning agent's ID
     * @return the number of dispatches recorded for this agent
     */
    long getDispatchCount(String agentId);

    /**
     * Get the total dispatch count across all agents.
     *
     * @return the sum of dispatches across all agents
     */
    long getTotalDispatchCount();

    /**
     * Get the storage count for a specific agent.
     *
     * @param agentId the owning agent's ID
     * @return the number of storage events recorded for this agent
     */
    long getStorageCount(String agentId);

    /**
     * Get the total storage count across all agents.
     *
     * @return the sum of storage events across all agents
     */
    long getTotalStorageCount();

    /**
     * Get the number of files in the output directory.
     * <p>
     * Delegates to {@link com.hdekker.ai_workflow.application.file.port.FileCounterPort}
     * when configured. Returns 0 when no output directory is set.
     *
     * @return the number of files in the output directory, or 0 if not configured
     */
    long getOutputDirectoryFileCount();
}

package com.hdekker.ai_workflow.application.pipeline.port;

import java.nio.file.Path;
import java.util.List;

import com.hdekker.ai_workflow.domain.pipeline.AgentMetrics;
import com.hdekker.ai_workflow.domain.pipeline.RegexFilterEntry;

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

    // -- filter (regex rejection) methods --

    /**
     * Record that a file was rejected by an agent's input regex filter.
     * <p>
     * Increments the per-agent filter counter and appends an entry to the
     * per-agent ring buffer (capacity 10).
     *
     * @param agentId the owning agent's ID
     * @param fileUrl the URL of the rejected file
     * @param regex   the regex pattern that rejected the file
     */
    void recordFilter(String agentId, String fileUrl, String regex);

    /**
     * Get the filter (regex rejection) count for a specific agent.
     *
     * @param agentId the owning agent's ID
     * @return the number of filter rejections recorded for this agent
     */
    long getFilterCount(String agentId);

    /**
     * Get the total filter count across all agents.
     *
     * @return the sum of filter rejections across all agents
     */
    long getTotalFilterCount();

    /**
     * Get the last filtered (rejected) file entries for a specific agent.
     * <p>
     * Returns at most 10 entries (the ring buffer capacity), ordered
     * oldest-first.
     *
     * @param agentId the owning agent's ID
     * @return the last filtered entries, or an empty list if none
     */
    List<RegexFilterEntry> getLastFilteredEntries(String agentId);

    /**
     * Get a consolidated metrics snapshot for a specific agent.
     * <p>
     * Combines dispatch count, filter count, and last filtered entries
     * into a single {@link AgentMetrics} record. Assembled from the
     * service's own maps — no external calls.
     *
     * @param agentId the owning agent's ID
     * @return a consolidated metrics snapshot for the agent
     */
    AgentMetrics getAgentMetrics(String agentId);
}

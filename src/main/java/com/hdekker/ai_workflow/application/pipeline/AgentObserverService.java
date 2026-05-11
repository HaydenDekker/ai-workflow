package com.hdekker.ai_workflow.application.pipeline;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import com.hdekker.ai_workflow.application.file.port.FileCounterPort;
import com.hdekker.ai_workflow.application.pipeline.port.AgentObserverPort;
import com.hdekker.ai_workflow.domain.pipeline.AgentMetrics;
import com.hdekker.ai_workflow.domain.pipeline.RegexFilterEntry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
     * Thread-safe store of per-agent filter (regex rejection) counters.
     */
    private final ConcurrentHashMap<String, Long> filterCounters = new ConcurrentHashMap<>();

    /**
     * Per-agent ring buffer of rejected-file entries.
     * <p>
     * Each {@link ArrayDeque} holds at most 10 entries (capacity constant).
     * Thread safety is ensured by synchronizing on the deque reference.
     */
    private final ConcurrentHashMap<String, ArrayDeque<RegexFilterEntry>> filterHistory
            = new ConcurrentHashMap<>();

    /** Maximum number of filter entries kept per agent in the ring buffer. */
    private static final int FILTER_HISTORY_CAPACITY = 10;

    /**
     * Output directory path for file count queries.
     */
    private final String outputDirectory;

    /**
     * File counter port for counting files in the output directory.
     */
    private final FileCounterPort fileCounter;

    /**
     * Construct the service with output directory and file counter for file count queries.
     * <p>
     * Uses {@code scanner.url} property (same as the persister) so both
     * the file counter and the persister always reference the same directory.
     *
     * @param fileCounter     the file counter port for counting files (nullable)
     * @param outputDirectory the output directory path to count files in (nullable)
     */
    @Autowired
    public AgentObserverService(FileCounterPort fileCounter,
                                @Value("${ai.workflow.output.directory:default}") String outputDirectory) {
        this.fileCounter = fileCounter;
        this.outputDirectory = outputDirectory;
        log.info("AgentObserverService initialized: outputDirectory={}, fileCounter={}",
                this.outputDirectory, this.fileCounter != null ? "present" : "null");
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

    /**
     * Get the number of files in the output directory.
     * <p>
     * Delegates to the configured {@link FileCounterPort}. Returns 0
     * when no output directory or file counter is configured.
     *
     * @return the number of files in the output directory, or 0 if not configured
     */
    @Override
    public long getOutputDirectoryFileCount() {
        if (fileCounter == null || outputDirectory == null) {
            return 0;
        }
        return fileCounter.countFiles(outputDirectory);
    }

    // -- filter (regex rejection) methods --

    /**
     * Record that a file was rejected by an agent's input regex filter.
     * <p>
     * Atomically increments the filter counter and appends an entry to the
     * per-agent ring buffer. Oldest entry is evicted when capacity (10) is exceeded.
     *
     * @param agentId the owning agent's ID
     * @param fileUrl the URL of the rejected file
     * @param regex   the regex pattern that rejected the file
     */
    @Override
    public void recordFilter(String agentId, String fileUrl, String regex) {
        filterCounters.merge(agentId, 1L, Long::sum);

        RegexFilterEntry entry = RegexFilterEntry.rejected(agentId, fileUrl, regex);
        ArrayDeque<RegexFilterEntry> deque = filterHistory.computeIfAbsent(
                agentId, k -> new ArrayDeque<>(FILTER_HISTORY_CAPACITY));
        synchronized (deque) {
            deque.addLast(entry);
            while (deque.size() > FILTER_HISTORY_CAPACITY) {
                deque.removeFirst();
            }
        }

        log.debug("Recorded filter rejection for agent {}: file={}, regex={}",
                agentId, fileUrl, regex);
    }

    /**
     * Get the filter (regex rejection) count for a specific agent.
     *
     * @param agentId the owning agent's ID
     * @return the number of filter rejections recorded for this agent, or 0 if none
     */
    @Override
    public long getFilterCount(String agentId) {
        return filterCounters.getOrDefault(agentId, 0L);
    }

    /**
     * Get the total filter count across all agents.
     *
     * @return the sum of filter rejections across all agents
     */
    @Override
    public long getTotalFilterCount() {
        return filterCounters.values().stream().mapToLong(Long::longValue).sum();
    }

    /**
     * Get the last filtered (rejected) file entries for a specific agent.
     * <p>
     * Returns an unmodifiable copy of the ring buffer contents, ordered oldest-first.
     *
     * @param agentId the owning agent's ID
     * @return the last filtered entries (max 10), or an empty list if none
     */
    @Override
    public List<RegexFilterEntry> getLastFilteredEntries(String agentId) {
        ArrayDeque<RegexFilterEntry> deque = filterHistory.get(agentId);
        if (deque == null) {
            return Collections.emptyList();
        }
        synchronized (deque) {
            return Collections.unmodifiableList(new java.util.ArrayList<>(deque));
        }
    }

    /**
     * Get a consolidated metrics snapshot for a specific agent.
     * <p>
     * Assembles dispatch count, filter count, and last filtered entries
     * from the service's own maps into a single {@link AgentMetrics} record.
     * No external calls are made.
     *
     * @param agentId the owning agent's ID
     * @return a consolidated metrics snapshot for the agent
     */
    @Override
    public AgentMetrics getAgentMetrics(String agentId) {
        long dispatches = getDispatchCount(agentId);
        long filters = getFilterCount(agentId);
        List<RegexFilterEntry> entries = getLastFilteredEntries(agentId);
        return new AgentMetrics(dispatches, filters, entries);
    }
}

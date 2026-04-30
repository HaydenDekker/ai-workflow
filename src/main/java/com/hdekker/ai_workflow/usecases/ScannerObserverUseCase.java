package com.hdekker.ai_workflow.usecases;

import com.hdekker.ai_workflow.rest.dto.ScannerMetricsSnapshot;
import com.hdekker.ai_workflow.ui.events.ScannerMetricsChangedEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Central use case for scanner metrics observation.
 * <p>
 * Tracks per-agent discovered counts and emission timestamps.
 * The file count is always computed on-demand by walking the watched directory,
 * eliminating drift between incremental counters and actual filesystem state.
 * <p>
 * Core responsibilities:
 * <ul>
 *   <li>Accept a single event type via {@link #recordScannerEvent(ScannerEventType, String, String)}</li>
 *   <li>Track per-agent discovered count and last emission timestamp</li>
 *   <li>Compute file count on-demand from the filesystem in {@link #getMetrics(String)}</li>
 *   <li>Expose query methods for the UI layer</li>
 *   <li>Support callback registration for real-time UI push notifications</li>
 * </ul>
 *
 * @see ScannerMetricsSnapshot
 * @see ScannerMetricsChangedEvent
 */
@Service
public class ScannerObserverUseCase {

    private static final Logger log = LoggerFactory.getLogger(ScannerObserverUseCase.class);

    /**
     * Thread-safe store of per-agent metrics.
     */
    private final ConcurrentHashMap<String, AgentMetrics> metricsStore = new ConcurrentHashMap<>();

    /**
     * Agent ID to folder path mapping, used to walk the directory on-demand.
     */
    private final ConcurrentHashMap<String, String> agentFolders = new ConcurrentHashMap<>();

    /**
     * Registered callbacks for real-time UI push.
     */
    private final CopyOnWriteArrayList<Consumer<ScannerMetricsChangedEvent>> refreshCallbacks
            = new CopyOnWriteArrayList<>();

    /**
     * Internal record holding per-agent metric counters.
     */
    static class AgentMetrics {
        final long totalDiscovered;
        final LocalDateTime lastEmissionTimestamp;

        AgentMetrics(long totalDiscovered, LocalDateTime lastEmissionTimestamp) {
            this.totalDiscovered = totalDiscovered;
            this.lastEmissionTimestamp = lastEmissionTimestamp;
        }

        AgentMetrics(long totalDiscovered) {
            this(totalDiscovered, null);
        }

        AgentMetrics withDiscovered() {
            return new AgentMetrics(totalDiscovered + 1, lastEmissionTimestamp);
        }

        AgentMetrics withLastEmission(LocalDateTime timestamp) {
            return new AgentMetrics(totalDiscovered, timestamp);
        }
    }

    /**
     * Record a scanner event for the given agent.
     * <p>
     * {@code CREATION} and {@code MODIFICATION} increment the discovered counter.
     * {@code DELETION} and {@code UNCHANGED} do not.
     * The folder path is stored so {@link #getMetrics(String)} can walk the directory
     * to compute the current file count on-demand.
     *
     * @param eventType the type of event
     * @param agentId   the owning agent's ID
     * @param folderPath the watched directory path
     */
    public void recordScannerEvent(ScannerEventType eventType, String agentId, String folderPath) {
        agentFolders.put(agentId, folderPath);
        metricsStore.compute(agentId, (key, existing) -> {
            if (existing == null) {
                long discovered = (eventType == ScannerEventType.CREATION
                        || eventType == ScannerEventType.MODIFICATION) ? 1 : 0;
                return new AgentMetrics(discovered);
            }
            return (eventType == ScannerEventType.CREATION
                    || eventType == ScannerEventType.MODIFICATION)
                    ? existing.withDiscovered()
                    : existing;
        });
        pushToUI(ScannerMetricsChangedEvent.scannerEvent(eventType, agentId));
    }

    /**
     * Record a file emission event for the given agent.
     * <p>
     * Updates the last emission timestamp, which is used by the idle checker
     * to determine when a scanner should transition to IDLE.
     *
     * @param agentId the owning agent's ID
     */
    public void recordEmission(String agentId) {
        LocalDateTime now = LocalDateTime.now();
        metricsStore.compute(agentId, (key, existing) -> {
            if (existing == null) {
                return new AgentMetrics(0, now);
            }
            return existing.withLastEmission(now);
        });
        pushToUI(ScannerMetricsChangedEvent.fileCountUpdated(agentId));
    }

    /**
     * Check whether the given agent's scanner is idle.
     * <p>
     * A scanner is considered idle if no emission has occurred for
     * at least 30 seconds.
     *
     * @param agentId the owning agent's ID
     * @return true if the scanner is idle, false otherwise
     */
    public boolean isIdle(String agentId) {
        AgentMetrics metrics = metricsStore.get(agentId);
        if (metrics == null || metrics.lastEmissionTimestamp == null) {
            return true;
        }
        return Duration.between(metrics.lastEmissionTimestamp, LocalDateTime.now())
                .getSeconds() >= 30;
    }

    /**
     * Get the last emission timestamp for the given agent.
     *
     * @param agentId the owning agent's ID
     * @return the last emission timestamp, or null if none
     */
    public LocalDateTime getLastEmissionTimestamp(String agentId) {
        AgentMetrics metrics = metricsStore.get(agentId);
        return metrics != null ? metrics.lastEmissionTimestamp : null;
    }

    /**
     * Get a metrics snapshot for a specific agent.
     * <p>
     * The file count is computed on-demand by walking the watched directory.
     *
     * @param agentId the owning agent's ID
     * @return a snapshot of scanner metrics, or a zeroed snapshot if the agent has no recorded metrics
     */
    public ScannerMetricsSnapshot getMetrics(String agentId) {
        AgentMetrics metrics = metricsStore.get(agentId);
        long discovered = metrics != null ? metrics.totalDiscovered : 0;
        long fileCount = countFiles(agentId);
        return new ScannerMetricsSnapshot(agentId, fileCount, discovered);
    }

    /**
     * Count the number of regular files in the folder associated with the given agent.
     *
     * @param agentId the owning agent's ID
     * @return the number of regular files, or 0 if the agent has no folder or the directory cannot be walked
     */
    public long countFiles(String agentId) {
        String folderPath = agentFolders.get(agentId);
        if (folderPath == null) {
            return 0;
        }
        try {
            return Files.walk(Path.of(folderPath).toAbsolutePath())
                    .filter(Files::isRegularFile)
                    .count();
        } catch (IOException e) {
            log.warn("Failed to count files for agent {}: {}", agentId, e.getMessage());
            return 0;
        }
    }

    /**
     * Get metrics snapshots for all registered agents.
     *
     * @return a list of all agent metric snapshots
     */
    public List<ScannerMetricsSnapshot> getAllMetrics() {
        List<ScannerMetricsSnapshot> result = new ArrayList<>();
        for (String agentId : metricsStore.keySet()) {
            result.add(getMetrics(agentId));
        }
        return result;
    }

    /**
     * Register a callback for real-time UI push notifications.
     * <p>
     * Called by the UI view when attaching, so background threads (watch service)
     * can push updates via {@link #pushToUI(ScannerMetricsChangedEvent)}.
     *
     * @param callback the refresh callback
     */
    public void registerRefreshCallback(Consumer<ScannerMetricsChangedEvent> callback) {
        refreshCallbacks.add(callback);
        log.debug("Scanner metrics refresh callback registered");
    }

    /**
     * Unregister a previously registered callback.
     *
     * @param callback the callback to remove
     */
    public void unregisterRefreshCallback(Consumer<ScannerMetricsChangedEvent> callback) {
        refreshCallbacks.remove(callback);
        log.debug("Scanner metrics refresh callback unregistered");
    }

    /**
     * Push a metrics event to all registered UI callbacks.
     *
     * @param event the metrics change event
     */
    public void pushToUI(ScannerMetricsChangedEvent event) {
        for (Consumer<ScannerMetricsChangedEvent> callback : refreshCallbacks) {
            try {
                callback.accept(event);
            } catch (Exception e) {
                log.warn("Error in metrics refresh callback for agent {}: {}",
                        event.getAgentId(), e.getMessage());
            }
        }
    }
}

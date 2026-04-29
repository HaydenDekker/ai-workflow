package com.hdekker.ai_workflow.usecases;

import com.hdekker.ai_workflow.rest.dto.ScannerMetricsSnapshot;
import com.hdekker.ai_workflow.ui.events.ScannerMetricsChangedEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Central use case for scanner metrics observation.
 * <p>
 * Replaces scattered Micrometer-based metrics in {@code FileSystemScannerAdapter},
 * {@code NativeFileWatcher}, and {@code ScannerMetricsService} with a single,
 * thread-safe, in-memory metrics store keyed by agentId.
 * <p>
 * Core responsibilities:
 * <ul>
 *   <li>Track per-agent file count, total discovered, and unchanged counts</li>
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
     * Registered callbacks for real-time UI push.
     */
    private final CopyOnWriteArrayList<Consumer<ScannerMetricsChangedEvent>> refreshCallbacks
            = new CopyOnWriteArrayList<>();

    /**
     * Internal record holding per-agent metric counters.
     */
    static class AgentMetrics {
        final long fileCount;
        final long totalDiscovered;
        final long unchanged;

        AgentMetrics(long fileCount, long totalDiscovered, long unchanged) {
            this.fileCount = fileCount;
            this.totalDiscovered = totalDiscovered;
            this.unchanged = unchanged;
        }

        AgentMetrics withFileCount(long newCount) {
            return new AgentMetrics(newCount, totalDiscovered, unchanged);
        }

        AgentMetrics withDiscovered() {
            return new AgentMetrics(fileCount, totalDiscovered + 1, unchanged);
        }

        AgentMetrics withUnchanged() {
            return new AgentMetrics(fileCount, totalDiscovered, unchanged + 1);
        }
    }

    /**
     * Record a file discovery event for the given agent.
     *
     * @param agentId the owning agent's ID
     */
    public void recordDiscovery(String agentId) {
        metricsStore.compute(agentId, (key, existing) -> {
            if (existing == null) {
                return new AgentMetrics(0, 1, 0);
            }
            return existing.withDiscovered();
        });
        pushToUI(ScannerMetricsChangedEvent.fileDiscovered(agentId));
    }

    /**
     * Record an unchanged file event for the given agent.
     *
     * @param agentId the owning agent's ID
     */
    public void recordUnchanged(String agentId) {
        metricsStore.compute(agentId, (key, existing) -> {
            if (existing == null) {
                return new AgentMetrics(0, 0, 1);
            }
            return existing.withUnchanged();
        });
        pushToUI(ScannerMetricsChangedEvent.fileUnchanged(agentId));
    }

    /**
     * Update the file count for the given agent.
     *
     * @param agentId the owning agent's ID
     * @param count   the current number of files in the target directory
     */
    public void updateFileCount(String agentId, long count) {
        metricsStore.compute(agentId, (key, existing) -> {
            if (existing == null) {
                return new AgentMetrics(count, 0, 0);
            }
            return existing.withFileCount(count);
        });
        pushToUI(ScannerMetricsChangedEvent.fileCountUpdated(agentId));
    }

    /**
     * Get a metrics snapshot for a specific agent.
     *
     * @param agentId the owning agent's ID
     * @return a snapshot of scanner metrics, or a zeroed snapshot if the agent has no recorded metrics
     */
    public ScannerMetricsSnapshot getMetrics(String agentId) {
        AgentMetrics metrics = metricsStore.get(agentId);
        if (metrics == null) {
            return new ScannerMetricsSnapshot(agentId, 0, 0, 0);
        }
        return new ScannerMetricsSnapshot(agentId, metrics.fileCount, metrics.totalDiscovered, metrics.unchanged);
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
     * <p>
     * Called by {@link #recordDiscovery}, {@link #recordUnchanged}, and
     * {@link #updateFileCount} to notify the UI of changes.
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

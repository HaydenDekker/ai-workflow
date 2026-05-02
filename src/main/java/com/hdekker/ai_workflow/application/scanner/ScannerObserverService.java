package com.hdekker.ai_workflow.application.scanner;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.hdekker.ai_workflow.application.file.port.FileCounterPort;
import com.hdekker.ai_workflow.application.scanner.port.ScannerMetricsPort;
import com.hdekker.ai_workflow.domain.scanner.ScannerEventType;
import com.hdekker.ai_workflow.domain.scanner.ScannerMetrics;
import com.hdekker.ai_workflow.domain.scanner.ScannerMetricsEvent;
import com.hdekker.ai_workflow.domain.scanner.ScannerStatus;

/**
 * Central service for scanner metrics observation.
 * <p>
 * Implements {@link ScannerMetricsPort} — the application-layer port
 * for scanner metrics. Tracks per-agent discovered counts and emission
 * timestamps. The file count is always computed on-demand by walking
 * the watched directory via {@link FileCounterPort}.
 * <p>
 * Core responsibilities:
 * <ul>
 *   <li>Accept events via {@link #recordEvent(String, ScannerEventType, ScannerStatus, String, String)}</li>
 *   <li>Track per-agent discovered count and last emission timestamp</li>
 *   <li>Compute file count on-demand via {@link #countFiles(String)}</li>
 *   <li>Expose query methods for the application layer</li>
 *   <li>Support callback registration for real-time UI push notifications</li>
 * </ul>
 *
 * @see ScannerMetricsPort
 * @see ScannerMetricsPort
 */
@Service
public class ScannerObserverService implements ScannerMetricsPort {

    private static final Logger log = LoggerFactory.getLogger(ScannerObserverService.class);

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
    private final java.util.concurrent.CopyOnWriteArrayList<java.util.function.Consumer<ScannerMetricsEvent>> refreshCallbacks
            = new java.util.concurrent.CopyOnWriteArrayList<>();

    /**
     * Adapter for counting files in a watched directory.
     */
    private final FileCounterPort fileCounter;

    /**
     * Construct the service with the given file counter port.
     *
     * @param fileCounter the port for counting files on the filesystem
     */
    public ScannerObserverService(FileCounterPort fileCounter) {
        this.fileCounter = fileCounter;
    }

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
     * Dispatch logic on {@code eventType}:
     * <ul>
     *   <li>CREATION / MODIFICATION — store folder, increment discovered, update emission timestamp</li>
     *   <li>DELETION / UNCHANGED — store folder, no discovered increment</li>
     *   <li>null (emission, error, recovery) — update emission timestamp if EMITTING_UPDATES</li>
     * </ul>
     */
    @Override
    public void recordEvent(String agentId, ScannerEventType eventType,
                            ScannerStatus status, String folderPath, String errorMessage) {
        if (eventType == ScannerEventType.CREATION || eventType == ScannerEventType.MODIFICATION) {
            agentFolders.put(agentId, folderPath);
            metricsStore.compute(agentId, (key, existing) -> {
                if (existing == null) {
                    return new AgentMetrics(1);
                }
                return existing.withDiscovered();
            });
        } else if (eventType == ScannerEventType.DELETION || eventType == ScannerEventType.UNCHANGED) {
            agentFolders.put(agentId, folderPath);
        } else if (eventType == null) {
            // Lifecycle events (emission, error, recovery)
            if (status == ScannerStatus.EMITTING_UPDATES) {
                LocalDateTime now = LocalDateTime.now();
                metricsStore.compute(agentId, (key, existing) -> {
                    if (existing == null) {
                        return new AgentMetrics(0, now);
                    }
                    return existing.withLastEmission(now);
                });
            }
        }

        pushToUI(agentId, status, eventType, folderPath, errorMessage);
    }

    /**
     * Record that a file was emitted, updating the last emission timestamp.
     */
    @Override
    public void recordEmission(String agentId) {
        LocalDateTime now = LocalDateTime.now();
        metricsStore.compute(agentId, (key, existing) -> {
            if (existing == null) {
                return new AgentMetrics(0, now);
            }
            return existing.withLastEmission(now);
        });
    }

    /**
     * Push a status change to all registered UI callbacks.
     */
    @Override
    public void pushToUI(String agentId, ScannerStatus status) {
        for (java.util.function.Consumer<ScannerMetricsEvent> callback : refreshCallbacks) {
            try {
                callback.accept(new ScannerMetricsEvent(agentId, status, null, null, null));
            } catch (Exception e) {
                log.warn("Error in metrics refresh callback for agent {}: {}",
                        agentId, e.getMessage());
            }
        }
    }

    /**
     * Push a full metrics event to all registered UI callbacks.
     */
    private void pushToUI(String agentId, ScannerStatus status, ScannerEventType eventType,
                          String folderPath, String errorMessage) {
        for (java.util.function.Consumer<ScannerMetricsEvent> callback : refreshCallbacks) {
            try {
                callback.accept(new ScannerMetricsEvent(agentId, status, eventType, folderPath, errorMessage));
            } catch (Exception e) {
                log.warn("Error in metrics refresh callback for agent {}: {}",
                        agentId, e.getMessage());
            }
        }
    }

    /**
     * Check whether the given agent's scanner is idle.
     * A scanner is considered idle if no emission has occurred for at least 30 seconds.
     */
    @Override
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
     */
    @Override
    public LocalDateTime getLastEmissionTimestamp(String agentId) {
        AgentMetrics metrics = metricsStore.get(agentId);
        return metrics != null ? metrics.lastEmissionTimestamp : null;
    }

    /**
     * Get a metrics snapshot for a specific agent.
     * <p>
     * The file count is computed on-demand by walking the watched directory.
     */
    @Override
    public ScannerMetrics getMetrics(String agentId) {
        AgentMetrics metrics = metricsStore.get(agentId);
        long discovered = metrics != null ? metrics.totalDiscovered : 0;
        long fileCount = countFiles(agentId);
        return new ScannerMetrics(agentId, discovered, getLastEmissionTimestamp(agentId));
    }

    /**
     * Count the number of regular files in the folder associated with the given agent.
     */
    @Override
    public long countFiles(String agentId) {
        String folderPath = agentFolders.get(agentId);
        if (folderPath == null) {
            return 0;
        }
        return fileCounter.countFiles(folderPath);
    }

    /**
     * Get metrics snapshots for all registered agents.
     */
    @Override
    public List<ScannerMetrics> getAllMetrics() {
        List<ScannerMetrics> result = new ArrayList<>();
        for (String agentId : metricsStore.keySet()) {
            result.add(getMetrics(agentId));
        }
        return result;
    }

    /**
     * Register a callback for real-time UI push notifications.
     * <p>
     * Called by the UI view when attaching, so background threads (watch service)
     * can push updates via {@link #pushToUI(String, ScannerStatus)}.
     */
    public void registerRefreshCallback(java.util.function.Consumer<ScannerMetricsEvent> callback) {
        refreshCallbacks.add(callback);
        log.debug("Scanner metrics refresh callback registered");
    }

    /**
     * Unregister a previously registered callback.
     */
    public void unregisterRefreshCallback(java.util.function.Consumer<ScannerMetricsEvent> callback) {
        refreshCallbacks.remove(callback);
        log.debug("Scanner metrics refresh callback unregistered");
    }

    /**
     * Store the folder path for a given agent, used on-demand file counting.
     */
    @Override
    public void storeFolder(String agentId, String folderPath) {
        agentFolders.put(agentId, folderPath);
    }
}

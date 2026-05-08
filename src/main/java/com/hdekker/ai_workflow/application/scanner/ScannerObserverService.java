package com.hdekker.ai_workflow.application.scanner;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hdekker.ai_workflow.application.scanner.port.ScannerMetricsPort;
import com.hdekker.ai_workflow.domain.scanner.ScannerEventType;
import com.hdekker.ai_workflow.domain.scanner.ScannerFileEvent;
import com.hdekker.ai_workflow.domain.scanner.ScannerFileResult;
import com.hdekker.ai_workflow.domain.scanner.ScannerMetrics;
import com.hdekker.ai_workflow.domain.scanner.ScannerMetricsEvent;
import com.hdekker.ai_workflow.domain.scanner.ScannerStatus;

import org.springframework.stereotype.Service;

/**
 * Central service for scanner metrics observation.
 * <p>
 * Implements {@link ScannerMetricsPort} — the application-layer port
 * for scanner metrics. Tracks per-agent discovered counts and emission
 * timestamps. The file count is received from the scanner during event
 * processing and stored alongside discovered metrics.
 * <p>
 * Core responsibilities:
 * <ul>
 *   <li>Accept events via {@link #recordEvent(String, ScannerEventType, long)}</li>
 *   <li>Track per-agent discovered count and last emission timestamp</li>
 *   <li>Store file count received from the scanner</li>
 *   <li>Expose query methods for the application layer</li>
 *   <li>Support callback registration for real-time UI push notifications</li>
 * </ul>
 *
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
     * Registered callbacks for real-time UI push.
     */
    private final java.util.concurrent.CopyOnWriteArrayList<Consumer<ScannerMetricsEvent>> refreshCallbacks
            = new java.util.concurrent.CopyOnWriteArrayList<>();

    /**
     * Construct the service — no file counter dependency needed.
     * File counts are received from the scanner during event processing.
     */
    public ScannerObserverService() {
    }

    /**
     * Internal record holding per-agent metric counters.
     */
    static class AgentMetrics {
        final long totalDiscovered;
        final LocalDateTime lastEmissionTimestamp;
        final long fileCount;

        AgentMetrics(long totalDiscovered, LocalDateTime lastEmissionTimestamp, long fileCount) {
            this.totalDiscovered = totalDiscovered;
            this.lastEmissionTimestamp = lastEmissionTimestamp;
            this.fileCount = fileCount;
        }

        AgentMetrics(long totalDiscovered, LocalDateTime lastEmissionTimestamp) {
            this(totalDiscovered, lastEmissionTimestamp, 0);
        }

        AgentMetrics(long totalDiscovered) {
            this(totalDiscovered, null, 0);
        }

        AgentMetrics withDiscovered() {
            return new AgentMetrics(totalDiscovered + 1, lastEmissionTimestamp, fileCount);
        }

        AgentMetrics withLastEmission(LocalDateTime timestamp) {
            return new AgentMetrics(totalDiscovered, timestamp, fileCount);
        }

        AgentMetrics withFileCount(long fileCount) {
            return new AgentMetrics(totalDiscovered, lastEmissionTimestamp, fileCount);
        }
    }

    /**
     * Record a scanner event for the given agent.
     * <p>
     * Dispatch logic on {@code eventType}:
     * <ul>
     *   <li>CREATION / MODIFICATION — increment discovered, store file count</li>
     *   <li>DELETION / UNCHANGED — store file count, no discovered increment</li>
     *   <li>null (lifecycle events) — store file count only</li>
     * </ul>
     */
    @Override
    public void recordEvent(String agentId, ScannerEventType eventType, long fileCount) {
        if (eventType == ScannerEventType.CREATION || eventType == ScannerEventType.MODIFICATION) {
            metricsStore.compute(agentId, (key, existing) -> {
                if (existing == null) {
                    return new AgentMetrics(1, null, fileCount);
                }
                return existing.withDiscovered().withFileCount(fileCount);
            });
        } else if (eventType == ScannerEventType.DELETION || eventType == ScannerEventType.UNCHANGED) {
            metricsStore.compute(agentId, (key, existing) -> {
                if (existing == null) {
                    return new AgentMetrics(0, null, fileCount);
                }
                return existing.withFileCount(fileCount);
            });
        } else if (eventType == null) {
            // Lifecycle events — store file count only
            metricsStore.compute(agentId, (key, existing) -> {
                if (existing == null) {
                    return new AgentMetrics(0, null, fileCount);
                }
                return existing.withFileCount(fileCount);
            });
        }

        pushToUI(agentId, null, eventType, null, null, fileCount);
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
     * <p>
     * Public instance method — not part of {@link ScannerMetricsPort}.
     * Called by {@link ScannerService} for lifecycle status pushes.
     *
     * @param agentId the owning agent's ID
     * @param status  the scanner status
     */
    public void pushToUI(String agentId, ScannerStatus status) {
        pushToUI(agentId, status, null, null, null, 0L);
    }

    /**
     * Push a full metrics event to all registered UI callbacks.
     */
    private void pushToUI(String agentId, ScannerStatus status, ScannerEventType eventType,
                          String folderPath, String errorMessage, long fileCount) {
        for (Consumer<ScannerMetricsEvent> callback : refreshCallbacks) {
            try {
                callback.accept(new ScannerMetricsEvent(agentId, status, eventType,
                        folderPath, errorMessage, fileCount));
            } catch (Exception e) {
                log.warn("Error in metrics refresh callback for agent {}: {}",
                        agentId, e.getMessage());
            }
        }
    }

    /**
     * Publish a file-level event to all registered callbacks.
     * <p>
     * Converts the file result into a {@link ScannerMetricsEvent} for backward
     * compatibility with existing callback consumers.
     *
     * @param event the file event to publish
     */
    public void publishFileEvent(ScannerFileEvent event) {
        ScannerStatus status = switch (event.result()) {
            case EMITTED -> ScannerStatus.EMITTING_UPDATES;
            case FILTERED -> ScannerStatus.FILTERED;
            case ERROR -> ScannerStatus.ERROR;
        };
        ScannerEventType eventType = event.result() == ScannerFileResult.EMITTED
                ? ScannerEventType.MODIFICATION : ScannerEventType.UNCHANGED;
        pushToUI(event.agentId(), status, eventType, event.folderPath(),
                event.errorMessage(), 0L);
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
     * Includes the file count stored at the last recorded event.
     */
    @Override
    public ScannerMetrics getMetrics(String agentId) {
        AgentMetrics metrics = metricsStore.get(agentId);
        long discovered = metrics != null ? metrics.totalDiscovered : 0;
        long fileCount = metrics != null ? metrics.fileCount : 0;
        return new ScannerMetrics(agentId, discovered, getLastEmissionTimestamp(agentId), fileCount);
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
    public void registerRefreshCallback(Consumer<ScannerMetricsEvent> callback) {
        refreshCallbacks.add(callback);
        log.debug("Scanner metrics refresh callback registered");
    }

    /**
     * Unregister a previously registered callback.
     */
    public void unregisterRefreshCallback(Consumer<ScannerMetricsEvent> callback) {
        refreshCallbacks.remove(callback);
        log.debug("Scanner metrics refresh callback unregistered");
    }

}

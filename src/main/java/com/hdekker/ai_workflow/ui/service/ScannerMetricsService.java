package com.hdekker.ai_workflow.ui.service;

import com.hdekker.ai_workflow.rest.dto.ScannerMetricsSnapshot;
import com.hdekker.ai_workflow.ui.events.ScannerMetricsChangedEvent;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Service to read scanner metrics from Micrometer's MeterRegistry for the UI.
 * <p>
 * Reads counters and gauges directly — no REST endpoints needed since Vaadin
 * runs server-side and can inject this service.
 * <p>
 * Provides a {@link #registerRefreshCallback(Consumer)} method that views
 * can call to register a refresh callback. When scanner metrics change,
 * {@link com.hdekker.ai_workflow.ui.service.ScannerMetricsPushService}
 * invokes this callback on the UI thread.
 *
 * @see com.hdekker.ai_workflow.files.FileSystemScannerAdapter
 */
@Service
public class ScannerMetricsService {

    private static final Logger log = LoggerFactory.getLogger(ScannerMetricsService.class);

    private static final String GAUGE_FILE_COUNT = "ai_workflow.scanner.file_count";
    private static final String COUNTER_DISCOVERED = "ai_workflow.scanner.files_discovered";
    private static final String COUNTER_UNCHANGED = "ai_workflow.scanner.files_unchanged";

    private final MeterRegistry registry;

    /**
     * Callback registered by the UI view to refresh the grid.
     * Called from background threads; the callback itself runs on the UI thread
     * via {@code UI.access()}.
     */
    private final AtomicReference<Consumer<ScannerMetricsChangedEvent>> refreshCallbackRef
            = new AtomicReference<>(event -> {});

    public ScannerMetricsService(MeterRegistry registry) {
        this.registry = registry;
    }

    /**
     * Register a refresh callback from the UI view.
     * <p>
     * Called by {@link com.hdekker.ai_workflow.ui.views.ScannerListView} when
     * the view attaches, so background threads (watch service) can push updates.
     *
     * @param callback the refresh callback
     */
    public void registerRefreshCallback(Consumer<ScannerMetricsChangedEvent> callback) {
        this.refreshCallbackRef.set(callback);
        log.info("Scanner metrics refresh callback registered");
    }

    /**
     * Get metrics for a specific scanner (by agentId).
     *
     * @param agentId the owning agent's ID
     * @return a snapshot of scanner metrics
     */
    public ScannerMetricsSnapshot getMetrics(String agentId) {
        double fileCount = getGaugeValue(GAUGE_FILE_COUNT, "agentId", agentId);
        double discovered = getCounterValue(COUNTER_DISCOVERED, "agentId", agentId);
        double unchanged = getCounterValue(COUNTER_UNCHANGED, "agentId", agentId);

        return new ScannerMetricsSnapshot(
                agentId,
                (long) fileCount,
                (long) discovered,
                (long) unchanged);
    }

    /**
     * Get the file count for all scanners combined.
     *
     * @return total file count across all scanners
     */
    public long getTotalFileCount() {
        return registry.find(GAUGE_FILE_COUNT).gauges().stream()
                .mapToDouble(Gauge::value)
                .mapToLong(Math::round)
                .sum();
    }

    /**
     * Get the total discovered files across all scanners.
     *
     * @return total discovered files
     */
    public long getTotalDiscovered() {
        return registry.find(COUNTER_DISCOVERED).counters().stream()
                .mapToDouble(Counter::count)
                .mapToLong(Math::round)
                .sum();
    }

    /**
     * Push a metrics event to the registered UI callback.
     * <p>
     * Called by {@link com.hdekker.ai_workflow.ui.service.ScannerMetricsPushService}
     * on the background thread. The callback itself runs on the UI thread
     * via {@code UI.access()}.
     *
     * @param event the metrics change event
     */
    void pushToUI(ScannerMetricsChangedEvent event) {
        Consumer<ScannerMetricsChangedEvent> callback = refreshCallbackRef.get();
        if (callback != null) {
            callback.accept(event);
        }
    }

    private double getGaugeValue(String name, String tagKey, String tagValue) {
        try {
            Gauge gauge = registry.get(name).tag(tagKey, tagValue).gauge();
            return gauge != null ? gauge.value() : 0.0;
        } catch (Exception e) {
            return 0.0;
        }
    }

    private double getCounterValue(String name, String tagKey, String tagValue) {
        try {
            Counter counter = registry.get(name).tag(tagKey, tagValue).counter();
            return counter != null ? counter.count() : 0.0;
        } catch (Exception e) {
            return 0.0;
        }
    }
}

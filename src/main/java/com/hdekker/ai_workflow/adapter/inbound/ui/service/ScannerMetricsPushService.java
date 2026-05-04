package com.hdekker.ai_workflow.adapter.inbound.ui.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hdekker.ai_workflow.adapter.inbound.ui.event.ScannerMetricsChangedEvent;
import com.hdekker.ai_workflow.usecases.ScannerObserverUseCase;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/**
 * Listens for {@link ScannerMetricsChangedEvent} (published when files are
 * created/modified) and pushes real-time grid updates to the Vaadin UI.
 *
 * <p>
 * This service bridges the gap between the background watch service thread
 * and the Vaadin UI thread. When a file event fires, this method receives
 * the Spring event and calls {@code UI.access()} to schedule a grid refresh.
 *
 * @see com.hdekker.ai_workflow.adapter.inbound.ui.event.ScannerMetricsChangedEvent
 */
@Service
public class ScannerMetricsPushService {

    private static final Logger log = LoggerFactory.getLogger(ScannerMetricsPushService.class);

    private final ScannerObserverUseCase observer;

    @Autowired
    public ScannerMetricsPushService(ScannerObserverUseCase observer) {
        this.observer = observer;
    }

    /**
     * Listen for scanner metrics change events and push to the UI.
     * <p>
     * Called on the background thread (watch service thread).
     * The actual grid refresh runs on the Vaadin UI thread via
     * {@code UI.access()}, registered by the view in
     * {@link ScannerObserverUseCase#registerRefreshCallback}.
     *
     * @param event the metrics change event
     */
    @EventListener
    public void onScannerMetricsChanged(ScannerMetricsChangedEvent event) {
        log.debug("Received scanner metrics event: agent={}, type={}", event.getAgentId(), event.getType());
        observer.pushToUI(event);
    }
}

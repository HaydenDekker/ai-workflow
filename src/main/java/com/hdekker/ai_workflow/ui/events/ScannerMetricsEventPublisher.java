package com.hdekker.ai_workflow.ui.events;

import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Consumes {@link ScannerMetricsChangedEvent} and publishes them via Spring's
 * {@link ApplicationEventPublisher} so the UI can react in real time.
 *
 * <p>Wired into {@link com.hdekker.ai_workflow.files.NativeFileWatcherAdapter} so
 * events fire on every file create/modify/delete event.</p>
 */
@Component
public class ScannerMetricsEventPublisher implements Consumer<ScannerMetricsChangedEvent> {

    private static final Logger log = LoggerFactory.getLogger(ScannerMetricsEventPublisher.class);

    private final ApplicationEventPublisher eventPublisher;

    public ScannerMetricsEventPublisher(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void accept(ScannerMetricsChangedEvent event) {
        log.debug("Publishing scanner metrics event: agent={}, type={}", event.getAgentId(), event.getType());
        eventPublisher.publishEvent(event);
    }
}

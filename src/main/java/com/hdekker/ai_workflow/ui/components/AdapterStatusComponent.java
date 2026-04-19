package com.hdekker.ai_workflow.ui.components;

import com.hdekker.ai_workflow.rest.dto.AdapterStatus;
import com.hdekker.ai_workflow.rest.dto.LLMStatus;
import com.hdekker.ai_workflow.service.LLMStatusService;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Hr;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.DetachEvent;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reusable component displaying a single LLM endpoint's health status.
 *
 * Layout: Horizontal card with three columns:
 * - Column 1: Status icon + badge (fixed width)
 * - Column 2: Endpoint info (fixed width)
 * - Column 3: Details + refresh button (flex)
 *
 * Features:
 * - Auto-refresh at configurable interval (default 30 seconds)
 * - Manual refresh button
 * - Lifecycle-aware (stops scheduler on detach)
 */
public class AdapterStatusComponent extends HorizontalLayout {

    private static final Logger log = LoggerFactory.getLogger(AdapterStatusComponent.class);

    private static final int DEFAULT_REFRESH_INTERVAL_SECONDS = 30;

    private LLMStatus status;
    private final LLMStatusService service;
    private final int refreshIntervalSeconds;

    // UI Components
    private Icon statusIcon;
    private Div statusBadge;
    private TextField endpointField;
    private TextField lastCheckedField;
    private TextField modelCountField;
    private TextField modelNamesField;
    private TextField errorField;
    private com.vaadin.flow.component.button.Button refreshButton;

    // Auto-refresh scheduler
    private ScheduledExecutorService scheduler;

    /**
     * Creates an adapter status card with default 30-second auto-refresh.
     *
     * @param status current status of the LLM endpoint
     * @param service service for triggering health checks
     */
    public AdapterStatusComponent(LLMStatus status, LLMStatusService service) {
        this(status, service, DEFAULT_REFRESH_INTERVAL_SECONDS);
    }

    /**
     * Creates an adapter status card with custom refresh interval.
     *
     * @param status current status of the LLM endpoint
     * @param service service for triggering health checks
     * @param refreshIntervalSeconds interval for auto-refresh (0 to disable)
     */
    public AdapterStatusComponent(LLMStatus status, LLMStatusService service,
                                  int refreshIntervalSeconds) {
        this.status = status;
        this.service = service;
        this.refreshIntervalSeconds = refreshIntervalSeconds;

        initLayout();
        updateDisplay();
        startAutoRefresh();
    }

    private void initLayout() {
        // Main layout configuration
        setPadding(false);
        setSpacing(false);
        addClassName("adapter-status-card");
        setWidthFull();
        setAlignItems(FlexComponent.Alignment.STRETCH);

        // Column 1: Status Indicator (80px)
        VerticalLayout statusColumn = createStatusColumn();
        add(statusColumn);

        // Column 2: Endpoint Info (250px)
        VerticalLayout infoColumn = createInfoColumn();
        add(infoColumn);

        // Column 3: Details + Refresh (flex)
        VerticalLayout detailsColumn = createDetailsColumn();
        add(detailsColumn);
    }

    private VerticalLayout createStatusColumn() {
        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(false);
        layout.setSpacing(true);
        layout.setWidth("80px");
        layout.setAlignItems(FlexComponent.Alignment.CENTER);

        statusIcon = new Icon(VaadinIcon.CIRCLE);
        statusIcon.setSize("48px");
        layout.add(statusIcon);

        statusBadge = new Div();
        statusBadge.addClassName("status-badge");
        layout.add(statusBadge);

        return layout;
    }

    private VerticalLayout createInfoColumn() {
        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(false);
        layout.setSpacing(true);
        layout.setWidth("250px");

        endpointField = new TextField();
        endpointField.setReadOnly(true);
        endpointField.setWidthFull();
        endpointField.addClassName("endpoint-name");
        layout.add(endpointField);

        lastCheckedField = new TextField();
        lastCheckedField.setReadOnly(true);
        lastCheckedField.setWidthFull();
        lastCheckedField.addClassName("last-checked");
        layout.add(lastCheckedField);

        return layout;
    }

    private VerticalLayout createDetailsColumn() {
        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(false);
        layout.setSpacing(true);
        layout.setWidthFull();

        modelCountField = new TextField();
        modelCountField.setReadOnly(true);
        modelCountField.setWidthFull();
        modelCountField.addClassName("model-count");
        layout.add(modelCountField);

        modelNamesField = new TextField();
        modelNamesField.setReadOnly(true);
        modelNamesField.setWidthFull();
        modelNamesField.addClassName("model-names");
        layout.add(modelNamesField);

        errorField = new TextField();
        errorField.setReadOnly(true);
        errorField.setWidthFull();
        errorField.addClassName("error-message");
        errorField.setVisible(false);
        layout.add(errorField);

        Hr separator = new Hr();
        separator.setClassName("card-separator");
        layout.add(separator);

        refreshButton = new com.vaadin.flow.component.button.Button("Refresh",
                e -> refreshStatus());
        refreshButton.setIcon(new Icon(VaadinIcon.REFRESH));
        refreshButton.addClassName("refresh-btn");
        refreshButton.addThemeVariants(
                com.vaadin.flow.component.button.ButtonVariant.LUMO_TERTIARY);
        layout.add(refreshButton);

        return layout;
    }

    /**
     * Update display based on current status.
     */
    public void updateDisplay() {
        // Endpoint name
        String endpointName = extractEndpointName(status.endpoint());
        endpointField.setValue("Endpoint: " + endpointName);

        // Last checked
        if (status.lastChecked() != null) {
            String timeAgo = calculateTimeAgo(status.lastChecked());
            lastCheckedField.setValue("Last checked: " + timeAgo);
        } else {
            lastCheckedField.setValue("Last checked: Never");
        }

        // Model count
        if (status.modelCount() != null && status.modelCount() > 0) {
            modelCountField.setValue(status.modelCount() + " model(s) available");
            modelCountField.setVisible(true);
        } else {
            modelCountField.setVisible(false);
        }

        // Model names
        if (status.modelNames() != null && !status.modelNames().isEmpty()) {
            String names = String.join(", ", status.modelNames());
            if (names.length() > 50) {
                names = names.substring(0, 47) + "...";
            }
            modelNamesField.setValue(names);
            modelNamesField.setVisible(true);
        } else {
            modelNamesField.setVisible(false);
        }

        // Error message
        if (status.errorMessage() != null && !status.errorMessage().isEmpty()) {
            errorField.setValue("Error: " + status.errorMessage());
            errorField.setVisible(true);
        } else {
            errorField.setVisible(false);
        }

        // Apply status styles
        applyStatusStyles(status.status());
    }

    private void applyStatusStyles(AdapterStatus adapterStatus) {
        // Reset classes on the badge
        statusBadge.getElement().getClassList().clear();

        switch (adapterStatus) {
            case UP:
                statusIcon.setIcon(VaadinIcon.CHECK_CIRCLE_O);
                statusIcon.setColor("var(--lumo-success-color)");
                statusBadge.setText("UP");
                statusBadge.addClassName("status-badge-up");
                break;

            case WARN:
                statusIcon.setIcon(VaadinIcon.EXCLAMATION_CIRCLE_O);
                statusIcon.setColor("var(--lumo-warning-color)");
                statusBadge.setText("WARN");
                statusBadge.addClassName("status-badge-warn");
                break;

            case DOWN:
                statusIcon.setIcon(VaadinIcon.CLOSE_CIRCLE_O);
                statusIcon.setColor("var(--lumo-error-color)");
                statusBadge.setText("DOWN");
                statusBadge.addClassName("status-badge-down");
                break;

            case CONNECTING:
                statusIcon.setIcon(VaadinIcon.SPINNER);
                statusIcon.setColor("var(--lumo-primary-color)");
                statusBadge.setText("CHECKING");
                statusBadge.addClassName("status-badge-connecting");
                break;

            default:
                statusIcon.setIcon(VaadinIcon.QUESTION_CIRCLE_O);
                statusIcon.setColor("var(--lumo-contrast-50pct)");
                statusBadge.setText("UNKNOWN");
                statusBadge.addClassName("status-badge-unknown");
        }
    }

    /**
     * Manually refresh status for this endpoint.
     */
    private void refreshStatus() {
        refreshButton.setEnabled(false);
        refreshButton.setIcon(new Icon(VaadinIcon.SPINNER));

        service.triggerPoll().stream()
                .filter(s -> s.endpoint().equals(status.endpoint()))
                .findFirst()
                .ifPresent(newStatus -> {
                    updateStatus(newStatus);
                    refreshButton.setEnabled(true);
                    refreshButton.setIcon(new Icon(VaadinIcon.REFRESH));
                });
    }

    private void updateStatus(LLMStatus newStatus) {
        // Update internal status reference
        this.status = newStatus;

        // Re-apply display
        updateDisplay();

        // Show notification
        Notification.show("Status updated: " + newStatus.status(),
                2000, Notification.Position.BOTTOM_START);
    }

    /**
     * Extract a friendly name from the endpoint URL.
     */
    private String extractEndpointName(String endpoint) {
        if (endpoint == null || endpoint.isEmpty()) {
            return "Unknown";
        }

        try {
            String withoutProtocol = endpoint.replace("http://", "")
                    .replace("https://", "");
            String withoutPort = withoutProtocol.split(":")[0];
            return withoutPort;
        } catch (Exception e) {
            return endpoint;
        }
    }

    /**
     * Calculate relative time ago from a timestamp.
     */
    private String calculateTimeAgo(LocalDateTime lastChecked) {
        if (lastChecked == null) {
            return "Never";
        }

        Duration duration = Duration.between(lastChecked, LocalDateTime.now());
        long seconds = duration.getSeconds();

        if (seconds < 0) {
            return "in the future";
        } else if (seconds < 60) {
            return seconds + " sec ago";
        } else if (seconds < 3600) {
            return (seconds / 60) + " min ago";
        } else if (seconds < 86400) {
            return (seconds / 3600) + " hr ago";
        } else {
            return (seconds / 86400) + " day(s) ago";
        }
    }

    /**
     * Start auto-refresh timer.
     */
    private void startAutoRefresh() {
        if (refreshIntervalSeconds <= 0) {
            return;
        }

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "adapter-status-refresh");
            t.setDaemon(true);
            return t;
        });

        scheduler.scheduleAtFixedRate(() -> {
            com.vaadin.flow.component.UI.getCurrent().access(() -> {
                try {
                    List<LLMStatus> updated = service.triggerPoll();
                    updated.stream()
                            .filter(s -> s.endpoint().equals(status.endpoint()))
                            .findFirst()
                            .ifPresent(this::updateStatus);
                } catch (Exception e) {
                    log.warn("Auto-refresh failed for endpoint {}",
                            status.endpoint(), e);
                }
            });
        }, refreshIntervalSeconds, refreshIntervalSeconds, TimeUnit.SECONDS);
    }

    /**
     * Stop auto-refresh timer.
     */
    private void stopAutoRefresh() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        stopAutoRefresh();
        super.onDetach(detachEvent);
    }
}

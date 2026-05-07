package com.hdekker.ai_workflow.adapter.inbound.ui.view;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hdekker.ai_workflow.adapter.inbound.rest.dto.ScannerInfoDTO;
import com.hdekker.ai_workflow.adapter.inbound.ui.service.ScannerService;
import com.hdekker.ai_workflow.application.scanner.ScannerObserverService;
import com.hdekker.ai_workflow.domain.scanner.ScannerMetrics;
import com.hdekker.ai_workflow.domain.scanner.ScannerMetricsEvent;

import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Hr;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.router.AfterNavigationEvent;
import com.vaadin.flow.router.AfterNavigationObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Scanner dashboard view displaying all scanners in a grid layout.
 *
 * Route: /scanners
 *
 * Features:
 * - Grid columns: Agent ID, Target Directory, Status, Created, Last Emitted, Files, Actions
 * - Status indicators with color-coded dots (IDLE=green, EMITTING_ALL=amber, EMITTING_UPDATES=blue, ERROR=red)
 * - Manual refresh button
 * - Auto-refresh every 30 seconds when view is visible
 * - Real-time file count updates when files are discovered
 * - Loading indicator during refresh
 * - Delete scanner action per row
 */
@Route("scanners")
@PageTitle("Scanners")
public class ScannerListView extends VerticalLayout
        implements AfterNavigationObserver {

    private static final Logger log = LoggerFactory.getLogger(ScannerListView.class);
    private static final int VIEW_REFRESH_SECONDS = 30;
    private static final DateTimeFormatter DATE_TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private Grid<ScannerInfoDTO> grid;
    private final ScannerService scannerService;
    private final ScannerObserverService observer;
    private ProgressBar loadingIndicator;
    private ScheduledExecutorService refreshScheduler;
    private Consumer<ScannerMetricsEvent> refreshCallback;
    @Autowired
    public ScannerListView(ScannerService scannerService, ScannerObserverService observer) {
        this.scannerService = scannerService;
        this.observer = observer;
        initLayout();
    }

    private void initLayout() {
        // Setup main layout
        setPadding(true);
        setSpacing(true);
        addClassName("scanners-view");

        // Header
        H2 header = new H2("File Scanners");
        header.addClassName("page-title");

        // Refresh button
        Button refreshButton = new Button("Refresh All", event -> loadScanners());
        refreshButton.setIcon(new Icon(VaadinIcon.REFRESH));
        refreshButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        HorizontalLayout headerLayout = new HorizontalLayout(header, refreshButton);
        headerLayout.setWidthFull();
        headerLayout.setJustifyContentMode(HorizontalLayout.JustifyContentMode.BETWEEN);

        // Loading indicator
        loadingIndicator = new ProgressBar();
        loadingIndicator.setVisible(false);
        loadingIndicator.setWidthFull();
        loadingIndicator.setIndeterminate(true);

        // Grid
        grid = new Grid<>(ScannerInfoDTO.class, false);
        grid.setWidthFull();
        grid.setHeight("600px");
        grid.addThemeVariants(GridVariant.LUMO_NO_BORDER, GridVariant.LUMO_COLUMN_BORDERS);

        // Configure columns
        grid.addColumn(ScannerInfoDTO::agentId)
            .setHeader("Agent")
            .setAutoWidth(true)
            .setSortable(true);

        grid.addColumn(ScannerInfoDTO::targetDirectory)
            .setHeader("Target Directory")
            .setFlexGrow(2)
            .setSortable(true);

        // Status column: render as colored indicator + text (component column)
        grid.addComponentColumn(this::renderStatusComponent)
            .setHeader("Status")
            .setAutoWidth(true);

        // Created column with date formatting
        grid.addColumn(info -> info.createdAt() != null ? info.createdAt().format(DATE_TIME_FMT) : "N/A")
            .setHeader("Created")
            .setAutoWidth(true)
            .setSortable(true);

        // Last emitted column with date formatting
        grid.addColumn(info -> info.lastEmittedAt() != null ? info.lastEmittedAt().format(DATE_TIME_FMT) : "N/A")
            .setHeader("Last Emitted")
            .setAutoWidth(true)
            .setSortable(true);

        // Files column: file count stored in the DTO by the scanner
        grid.addColumn(info -> {
            try {
                // fileCount is now part of ScannerInfoDTO — no separate observer call needed
                long count = info.fileCount() != null ? info.fileCount() : 0L;
                return count + " files";
            } catch (Exception e) {
                return "—";
            }
        })
            .setHeader("Files")
            .setAutoWidth(true);

        // Actions column: delete button per row (component column)
        grid.addComponentColumn(this::renderActionsColumn)
            .setHeader("Actions")
            .setAutoWidth(true);

        // Separator
        Hr separator = new Hr();

        // Add components in order
        add(headerLayout);
        add(loadingIndicator);
        add(separator);
        add(grid);

        // Register refresh callback so background threads can push real-time updates.
        // When a file is created/modified, the watch service fires an event,
        // ScannerMetricsPushService receives it and calls this callback.
        // The callback is wrapped with UI.access() so the grid refresh
        // runs on the Vaadin UI thread, not the background watch thread.
        //
        // IMPORTANT: We call loadScanners() (not grid.getDataProvider().refreshAll())
        // because ScannerInfo is an immutable record — the status value is baked in
        // at construction time. refreshAll() only re-renders existing items, it does
        // not fetch new data. To show updated status (e.g. IDLE → EMITTING_UPDATES),
        // we must re-fetch ScannerInfo from the service.
        addAttachListener(event -> {
            com.vaadin.flow.component.UI ui = event.getUI();
            refreshCallback = e -> {
                log.debug("UI refresh callback triggered: agent={}, type={}",
                        e.agentId(), e.eventType() != null ? e.eventType().name().toLowerCase() : e.status().name().toLowerCase());
                // Use quiet refresh — no loading indicator or toast.
                // loadScanners() shows loading + notification which is too noisy
                // for frequent file events. ScannerInfo is an immutable record so
                // we must re-fetch from the service (not just refreshAll()).
                ui.access(() -> refreshScanners());
            };
            observer.registerRefreshCallback(refreshCallback);
        });

        addDetachListener(event -> {
            // Clear the callback to avoid stale references
            if (refreshCallback != null) {
                observer.unregisterRefreshCallback(refreshCallback);
                refreshCallback = null;
            }
        });
    }

    /**
     * Render a status indicator: colored dot + status text as a Vaadin Div component.
     * <p>
     * If the status is ERROR and an error message is present, the dot is wrapped
     * in a tooltip showing the error.
     */
    private Div renderStatusComponent(ScannerInfoDTO info) {
        String status = info.status() != null ? info.status() : "UNKNOWN";
        String color;
        switch (status) {
            case "EMITTING_INITIAL" -> color = "#f5a623";  // amber
            case "EMITTING_UPDATES" -> color = "#4a90d9";  // blue
            case "FILTERED" -> color = "#e67e22";  // orange
            case "ERROR" -> color = "#e74c3c";  // red
            default -> color = "#27ae60";  // IDLE = green
        }
        Div dot = new Div();
        dot.getElement().getStyle().set("width", "10px");
        dot.getElement().getStyle().set("height", "10px");
        dot.getElement().getStyle().set("border-radius", "50%");
        dot.getElement().getStyle().set("background", color);
        dot.getElement().getStyle().set("display", "inline-block");
        dot.getElement().getStyle().set("margin-right", "6px");
        Span text = new Span(status);

        // Add tooltip for ERROR status with error message
        if ("ERROR".equals(status) && info.errorMessage() != null && !info.errorMessage().isBlank()) {
            dot.getElement().setAttribute("title", info.errorMessage());
        }

        StatusWrapper wrapper = new StatusWrapper(dot, text);
        return wrapper;
    }

    /**
     * Helper Div that renders its children (dot + text) horizontally.
     */
    private static class StatusWrapper extends Div {
        private static final long serialVersionUID = -2932962928480190387L;

		public StatusWrapper(Div dot, Span text) {
            super(dot, text);
            getElement().getStyle().set("display", "flex");
            getElement().getStyle().set("align-items", "center");
        }
    }

    /**
     * Render the delete action button per row.
     */
    private HorizontalLayout renderActionsColumn(ScannerInfoDTO info) {
        Button deleteBtn = new Button(new Icon(VaadinIcon.TRASH));
        deleteBtn.addClassName("scanner-delete-btn");
        deleteBtn.getElement().setAttribute("title", "Delete scanner");
        deleteBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        deleteBtn.addClickListener(event -> deleteScanner(info.agentId(), info.id()));
        return new HorizontalLayout(deleteBtn);
    }

    private void loadScanners() {
        showLoading(true);

        scannerService.getAllScannerInfos()
            .subscribe(
                scanners -> grid.getUI().get().access(() -> updateGrid(scanners, true)),
                error -> grid.getUI().get().access(() -> {
                    Notification.show("Error loading scanners: " + error.getMessage(), 4000, Notification.Position.MIDDLE);
                    showLoading(false);
                })
            );
    }

    /**
     * Quiet refresh — no loading indicator, no notification toast.
     * Called by the real-time metrics callback so frequent file events
     * don't spam the UI with loading states and toasts.
     * <p>
     * IMPORTANT: ScannerInfo is an immutable record — the status value is baked in
     * at construction time. We must re-fetch ScannerInfo from the service (not just
     * call grid.getDataProvider().refreshAll()) to show updated status values.
     */
    private void refreshScanners() {
        scannerService.getAllScannerInfos()
            .subscribe(
                scanners -> grid.getUI().get().access(() -> updateGrid(scanners, false)),
                error -> log.warn("Error refreshing scanners: {}", error.getMessage())
            );
    }

    private void updateGrid(List<ScannerInfoDTO> scanners, boolean notify) {
        grid.setItems(scanners);
        if (notify) {
            if (scanners.isEmpty()) {
                Notification.show("No scanners found", 3000, Notification.Position.MIDDLE);
            } else {
                Notification.show("Loaded " + scanners.size() + " scanner(s)", 2000, Notification.Position.BOTTOM_START);
            }
            showLoading(false);
        }
    }

    private void deleteScanner(String agentId, String scannerId) {
        Notification.show("Deleting scanner " + scannerId + " for agent " + agentId, 3000, Notification.Position.MIDDLE);
        scannerService.deleteScanner(agentId)
            .subscribe(
                v -> {
                    if (grid.getUI().isPresent()) {
                        grid.getUI().get().access(() -> loadScanners());
                    }
                },
                err -> {
                    if (grid.getUI().isPresent()) {
                        grid.getUI().get().access(() ->
                            Notification.show("Delete failed: " + err.getMessage(), 4000, Notification.Position.MIDDLE)
                        );
                    }
                }
            );
    }

    private void showLoading(boolean show) {
        loadingIndicator.setVisible(show);
        grid.setEnabled(!show);
    }

    @Override
    public void afterNavigation(AfterNavigationEvent event) {
        // Start auto-refresh
        startAutoRefresh();
        // Load initial data
        loadScanners();
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        stopAutoRefresh();
        super.onDetach(detachEvent);
    }

    private void startAutoRefresh() {
        if (refreshScheduler != null) {
            stopAutoRefresh();
        }

        refreshScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "scanners-view-refresh");
            t.setDaemon(true);
            return t;
        });

        refreshScheduler.scheduleAtFixedRate(() -> {
            com.vaadin.flow.component.UI.getCurrent().access(() -> {
                if (grid.getUI().isPresent()) {
                    loadScanners();
                }
            });
        }, VIEW_REFRESH_SECONDS, VIEW_REFRESH_SECONDS, TimeUnit.SECONDS);

        log.info("Auto-refresh started for ScannerListView (every {}s)", VIEW_REFRESH_SECONDS);
    }

    private void stopAutoRefresh() {
        if (refreshScheduler != null) {
            refreshScheduler.shutdownNow();
            refreshScheduler = null;
            log.info("Auto-refresh stopped for ScannerListView");
        }
    }
}

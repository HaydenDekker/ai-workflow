package com.hdekker.ai_workflow.adapter.inbound.ui.view;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import com.vaadin.flow.component.UI;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hdekker.ai_workflow.adapter.inbound.rest.dto.ScannerInfoDTO;
import com.hdekker.ai_workflow.adapter.inbound.ui.service.ScannerService;
import com.hdekker.ai_workflow.application.scanner.ScannerEventBus;
import com.hdekker.ai_workflow.domain.scanner.ScannerFileEvent;
import com.hdekker.ai_workflow.domain.scanner.ScannerFileResult;

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
    private final ScannerEventBus eventBus;
    private ProgressBar loadingIndicator;
    private ScheduledExecutorService refreshScheduler;
    private java.util.function.Consumer<ScannerFileEvent> refreshCallback;

    /**
     * Executor for scheduling display state auto-reset timers.
     */
    private ScheduledExecutorService displayTimerExecutor;

    /**
     * Per-agent display state and its associated auto-reset timer.
     * Keys are agent IDs; values hold the current display state string
     * and the {@link ScheduledFuture} that resets it to "Idle".
     */
    private final Map<String, DisplayTimerEntry> displayTimers = new ConcurrentHashMap<>();
    @Autowired
    public ScannerListView(ScannerService scannerService, ScannerEventBus eventBus) {
        this.scannerService = scannerService;
        this.eventBus = eventBus;
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
        // which is published through the event bus. The callback updates the
        // per-agent display state (Active / Filtered / Error) and schedules
        // an auto-reset timer to "Idle" where applicable. The callback is
        // wrapped with UI.access() so the grid refresh runs on the Vaadin UI thread.

        // Display timer executor for auto-resetting display states.
        displayTimerExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "scanners-view-display-timer");
            t.setDaemon(true);
            return t;
        });

        addAttachListener(event -> {
            com.vaadin.flow.component.UI ui = event.getUI();
            refreshCallback = e -> {
                log.debug("UI refresh callback triggered: agent={}, result={}",
                        e.agentId(), e.result());
                // Update per-agent display state from event, schedule auto-reset.
                ui.access(() -> handleDisplayEvent(e, ui));
                // Also re-fetch lifecycle status so the grid shows updated
                // service-side state (e.g. EMITTING_INITIAL → EMITTING_UPDATES).
                ui.access(() -> refreshScanners());
            };
            eventBus.registerCallback(refreshCallback);
        });

        addDetachListener(event -> {
            // Clear the callback to avoid stale references
            if (refreshCallback != null) {
                eventBus.unregisterCallback(refreshCallback);
                refreshCallback = null;
            }
            // Cancel all pending display timers
            for (DisplayTimerEntry entry : displayTimers.values()) {
                if (entry.timer != null) {
                    entry.timer.cancel(false);
                }
            }
            displayTimers.clear();
            // Shutdown the display timer executor
            if (displayTimerExecutor != null) {
                displayTimerExecutor.shutdownNow();
                displayTimerExecutor = null;
            }
        });
    }

    /**
     * Render a status indicator: colored dot + status text as a Vaadin Div component.
     * <p>
     * Display state is owned by the UI layer — derived from
     * {@link ScannerFileEvent} callbacks and stored in {@link #displayTimers}.
     * When no active display timer exists for the agent, the lifecycle status
     * from {@link ScannerInfoDTO} is used as fallback.
     * <p>
     * If the final display state is ERROR and an error message is present,
     * the dot is wrapped in a tooltip showing the error.
     */
    private Div renderStatusComponent(ScannerInfoDTO info) {
        String displayState = displayTimers.containsKey(info.agentId())
                ? displayTimers.get(info.agentId()).state()
                : null;
        String status = displayState != null ? displayState
                : (info.status() != null ? info.status() : "UNKNOWN");
        String color = displayColorForState(status);
        Div dot = new Div();
        dot.getElement().getStyle().set("width", "10px");
        dot.getElement().getStyle().set("height", "10px");
        dot.getElement().getStyle().set("border-radius", "50%");
        dot.getElement().getStyle().set("background", color);
        dot.getElement().getStyle().set("display", "inline-block");
        dot.getElement().getStyle().set("margin-right", "6px");
        Span text = new Span(status);

        // Add tooltip for ERROR state with error message
        if ("Error".equals(status) || "ERROR".equals(status)) {
            if (info.errorMessage() != null && !info.errorMessage().isBlank()) {
                dot.getElement().setAttribute("title", info.errorMessage());
            }
        }

        StatusWrapper wrapper = new StatusWrapper(dot, text);
        return wrapper;
    }

    /**
     * Handle a display event from the event bus.
     * <p>
     * Maps the file-level result to a UI display state, cancels any
     * existing timer for this agent, and schedules a new auto-reset
     * timer where applicable (Active for 10s, Filtered for 2s).
     * Error state persists until cleared — no auto-reset.
     *
     * @param event the file-level event from the event bus
     * @param ui the current Vaadin UI instance (required for timer callbacks)
     */
    private void handleDisplayEvent(ScannerFileEvent event, UI ui) {
        String agentId = event.agentId();

        // Cancel any existing timer for this agent
        displayTimers.computeIfPresent(
                agentId,
                (key, entry) -> {
                    if (entry.timer != null) {
                        entry.timer.cancel(false);
                    }
                    return null;
                }
        );

        // Map result to display state
        String newState = displayStateFromResult(event.result());

        // Schedule auto-reset timer where applicable
        long durationMs = timerDurationMsForResult(event.result());
        if (durationMs > 0) {
            ScheduledFuture<?> timer = displayTimerExecutor.schedule(() -> {
                ui.access(() -> {
                    displayTimers.remove(agentId);
                    if (grid.getUI().isPresent()) {
                        grid.getDataProvider().refreshAll();
                    }
                });
            }, durationMs, TimeUnit.MILLISECONDS);
            displayTimers.put(agentId, new DisplayTimerEntry(newState, timer));
        } else {
            // No auto-reset — persist until cleared (error state)
            displayTimers.put(agentId, new DisplayTimerEntry(newState, null));
        }

        // Refresh grid to show updated display state
        ui.access(() -> {
            if (grid.getUI().isPresent()) {
                grid.getDataProvider().refreshAll();
            }
        });
    }

    /**
     * Map a {@link ScannerFileResult} to its UI display state string.
     *
     * @param result the file-level result, may be null
     * @return the display state string
     */
    private static String displayStateFromResult(ScannerFileResult result) {
        return switch (result) {
            case EMITTED -> "Active";
            case FILTERED -> "Filtered";
            case ERROR -> "Error";
            case null -> "Idle";
        };
    }

    /**
     * Map a display state string to its color hex code.
     *
     * @param state the display state string
     * @return the hex color code for the state
     */
    private static String displayColorForState(String state) {
        return switch (state) {
            case "Active" -> "#4a90d9";  // blue
            case "Filtered" -> "#e67e22";  // orange
            case "Error" -> "#e74c3c";  // red
            default -> "#27ae60";  // Idle and unknown = green
        };
    }

    /**
     * Return the auto-reset timer duration in milliseconds for a result type.
     * Zero means no auto-reset — the state persists until manually cleared.
     *
     * @param result the file-level result, may be null
     * @return duration in milliseconds, or 0 for no auto-reset
     */
    private static long timerDurationMsForResult(ScannerFileResult result) {
        return switch (result) {
            case EMITTED -> 10_000L;   // Active for 10 seconds
            case FILTERED -> 2_000L;   // Filtered for 2 seconds
            case ERROR -> 0L;          // Error persists until cleared
            case null -> 0L;
        };
    }

    /**
     * Holds the current display state and associated auto-reset timer
     * for a single agent.
     *
     * @param state  the current display state string ("Active", "Filtered", "Error", "Idle")
     * @param timer  the scheduled future for auto-reset, null if no timer (persistent state)
     */
    private static class DisplayTimerEntry {

        private final String state;
        private final ScheduledFuture<?> timer;

        DisplayTimerEntry(String state, ScheduledFuture<?> timer) {
            this.state = state;
            this.timer = timer;
        }

        String state() {
            return state;
        }

        ScheduledFuture<?> timer() {
            return timer;
        }
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
     * Called by the real-time event callback so lifecycle status changes
     * (e.g. EMITTING_INITIAL → EMITTING_UPDATES) are reflected in the grid.
     * <p>
     * Display state (Active / Filtered / Error) is owned by the UI layer
     * via {@link #displayTimers} and updated immediately in
     * {@link #handleDisplayEvent} — this method handles lifecycle status.
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

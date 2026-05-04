package com.hdekker.ai_workflow.ui.views;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.hdekker.ai_workflow.rest.dto.LLMStatus;
import com.hdekker.ai_workflow.ui.components.AdapterStatusComponent;
import com.hdekker.ai_workflow.usecases.AgentStatusUsecase;

import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Hr;
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
 * Observability dashboard showing LLM adapter health status.
 *
 * Route: /observability
 *
 * Features:
 * - Status cards for each configured LLM endpoint
 * - Manual refresh button (Refresh All)
 * - Auto-refresh every 30 seconds when view is visible
 * - Loading indicator during refresh
 */
@Route("observability")
@PageTitle("Observability")
public class ObservabilityView extends VerticalLayout
        implements AfterNavigationObserver {

    private static final int VIEW_REFRESH_SECONDS = 30;

    private final AgentStatusUsecase llmStatusService;
    private VerticalLayout cardsContainer;
    private Button refreshButton;
    private ProgressBar loadingIndicator;

    // Auto-refresh for entire view
    private ScheduledExecutorService viewRefreshScheduler;

    @Autowired
    public ObservabilityView(AgentStatusUsecase llmStatusService) {
        this.llmStatusService = llmStatusService;
        initLayout();
    }

    private void initLayout() {
        // Setup main layout
        setPadding(true);
        setSpacing(true);
        addClassName("observability-view");

        // Header
        H2 header = new H2("LLM Adapter Status");
        header.addClassName("page-title");

        refreshButton = new Button("Refresh All", event -> loadStatusCards());
        refreshButton.setIcon(new Icon(VaadinIcon.REFRESH));
        refreshButton.addThemeVariants(
                com.vaadin.flow.component.button.ButtonVariant.LUMO_PRIMARY);

        HorizontalLayout headerLayout = new HorizontalLayout(header, refreshButton);
        headerLayout.setWidthFull();
        headerLayout.setJustifyContentMode(
                HorizontalLayout.JustifyContentMode.BETWEEN);

        // Loading indicator
        loadingIndicator = new ProgressBar();
        loadingIndicator.setVisible(false);
        loadingIndicator.setWidthFull();
        loadingIndicator.setIndeterminate(true);

        // Cards container
        cardsContainer = new VerticalLayout();
        cardsContainer.setWidthFull();
        cardsContainer.setSpacing(true);
        cardsContainer.addClassName("status-cards-container");

        // Separator
        Hr separator = new Hr();

        // Add components in order
        add(headerLayout);
        add(loadingIndicator);
        add(separator);
        add(cardsContainer);
    }

    @Override
    public void afterNavigation(AfterNavigationEvent event) {
        // Start view-level auto-refresh
        startViewAutoRefresh();

        // Load initial data
        loadStatusCards();
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        // Stop auto-refresh when view is detached
        stopViewAutoRefresh();
        super.onDetach(detachEvent);
    }

    private void loadStatusCards() {
        showLoading(true);
        refreshButton.setEnabled(false);

        // Fetch status data
        List<LLMStatus> statuses = llmStatusService.getCurrentStatus();

        // Clear existing cards
        cardsContainer.removeAll();

        if (statuses.isEmpty()) {
            Notification.show("No LLM endpoints configured",
                    3000, Notification.Position.MIDDLE);
            showLoading(false);
            refreshButton.setEnabled(true);
            return;
        }

        // Create card for each endpoint
        for (LLMStatus status : statuses) {
            AdapterStatusComponent card = new AdapterStatusComponent(
                    status, llmStatusService);
            cardsContainer.add(card);
        }

        Notification.show("Loaded " + statuses.size() + " endpoint(s)",
                2000, Notification.Position.BOTTOM_START);

        showLoading(false);
        refreshButton.setEnabled(true);
    }

    private void showLoading(boolean show) {
        loadingIndicator.setVisible(show);
        cardsContainer.setEnabled(!show);
        cardsContainer.getElement().getStyle().setOpacity(show ? "0.5" : "1.0");
    }

    private void startViewAutoRefresh() {
        if (viewRefreshScheduler != null) {
            stopViewAutoRefresh();
        }

        viewRefreshScheduler = Executors.newSingleThreadScheduledExecutor(
                r -> {
                    Thread t = new Thread(r, "observability-view-refresh");
                    t.setDaemon(true);
                    return t;
                });

        viewRefreshScheduler.scheduleAtFixedRate(() -> {
            com.vaadin.flow.component.UI.getCurrent().access(() -> {
                if (cardsContainer != null
                        && cardsContainer.getElement().getNode().isAttached()) {
                    loadStatusCards();
                }
            });
        }, VIEW_REFRESH_SECONDS, VIEW_REFRESH_SECONDS, TimeUnit.SECONDS);
    }

    private void stopViewAutoRefresh() {
        if (viewRefreshScheduler != null) {
            viewRefreshScheduler.shutdownNow();
            viewRefreshScheduler = null;
        }
    }
}

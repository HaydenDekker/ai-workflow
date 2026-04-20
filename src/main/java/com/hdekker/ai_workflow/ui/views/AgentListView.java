package com.hdekker.ai_workflow.ui.views;

import com.vaadin.flow.router.Route;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.router.AfterNavigationEvent;
import com.vaadin.flow.router.AfterNavigationObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.component.html.Hr;
import com.vaadin.flow.component.notification.Notification;

import com.hdekker.ai_workflow.rest.dto.AgentInfo;
import com.hdekker.ai_workflow.rest.dto.LLMStatus;
import com.hdekker.ai_workflow.ui.components.LlmStatusBadge;
import com.hdekker.ai_workflow.ui.service.AgentInfoService;
import com.hdekker.ai_workflow.service.LLMStatusService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Main dashboard view displaying AgentInfo in a grid layout.
 */
@Route("agents")
@PageTitle("Agent List")
public class AgentListView extends VerticalLayout implements AfterNavigationObserver {
    private final Grid<AgentInfo> grid;
    private final AgentInfoService agentInfoService;
    private final LLMStatusService llmStatusService;
    private final ProgressBar loadingIndicator;
    private final LlmStatusBadge llmStatusBadge;
    private ScheduledExecutorService refreshScheduler;

    @Autowired
    public AgentListView(AgentInfoService agentInfoService, LLMStatusService llmStatusService) {
        this.agentInfoService = agentInfoService;
        this.llmStatusService = llmStatusService;
        
        // Setup layout with styling
        addClassName("main-layout");
        setPadding(true);
        setSpacing(true);
        
        // Initialize loading indicator
        loadingIndicator = new ProgressBar();
        loadingIndicator.setVisible(false);
        loadingIndicator.setWidth("100%");
        
        // Header with LLM status badge
        H2 header = new H2("Agent List");
        header.addClassName("page-title");

        // LLM status badge
        llmStatusBadge = new LlmStatusBadge();
        llmStatusBadge.addClassName("llm-status-badge-compact");

        HorizontalLayout headerLayout = new HorizontalLayout(header, llmStatusBadge);
        headerLayout.setAlignItems(Alignment.BASELINE);
        headerLayout.setWidthFull();
        headerLayout.setJustifyContentMode(HorizontalLayout.JustifyContentMode.BETWEEN);
        
        // Grid component with container for styling
        VerticalLayout gridContainer = new VerticalLayout();
        gridContainer.addClassName("grid-container");
        gridContainer.setPadding(false);
        gridContainer.setSpacing(false);
        
        grid = new Grid<AgentInfo>();
        grid.setWidth("100%");
        grid.setHeight("400px");
        grid.setSizeFull();
        
        // Configure columns with proper sizing and sorting
        grid.addColumn(AgentInfo::id)
            .setHeader("ID")
            .setAutoWidth(true)
            .setSortable(true);
            
        grid.addColumn(agent -> agent.definition() != null ? agent.definition().title() : "N/A")
            .setHeader("Title")
            .setFlexGrow(1)
            .setSortable(true);
            
        grid.addColumn(agent -> agent.definition() != null ? agent.definition().agentType() : "N/A")
            .setHeader("Agent Type")
            .setAutoWidth(true)
            .setSortable(true);
            
        grid.addColumn(agent -> agent.definition() != null && agent.definition().fileInputRegex() != null 
            ? agent.definition().fileInputRegex() : "N/A")
            .setHeader("File Regex")
            .setFlexGrow(2);
            
        grid.addColumn(AgentInfo::source)
            .setHeader("Source")
            .setAutoWidth(true)
            .setSortable(true);
            
        grid.addColumn(AgentInfo::createdAt)
            .setHeader("Created")
            .setAutoWidth(true)
            .setSortable(true);
            
        grid.addColumn(AgentInfo::active)
            .setHeader("Active")
            .setAutoWidth(true)
            .setSortable(true);
        
        // Add grid to its container
        gridContainer.add(grid);
        
        // Add loading indicator
        add(loadingIndicator);
        
        // Add components to layout
        add(headerLayout);
        add(gridContainer);
        add(new Hr());
        
        // Add navigation and refresh buttons
        Button refreshButton = new Button("Refresh", event -> reloadData());
        Button createButton = new Button("New Agent", event -> {
            // Placeholder for new agent creation
            Notification.show("Create new Agent dialog will open here");
        });
        
        HorizontalLayout buttonLayout = new HorizontalLayout(createButton, refreshButton);
        add(buttonLayout);
        
    
    }

    private void reloadData() {
        showLoading(true);
        agentInfoService.getAllAgentInfos()
            .doFinally(signalType -> grid.getUI().get().access(() -> showLoading(false)))
            .subscribe(
                agentInfos -> grid.getUI().get().access(() -> updateGrid(agentInfos)),
                error -> grid.getUI().get().access(() -> {
                    Notification.show("Error loading data: " + error.getMessage());
                    showLoading(false);
                })
            );
    }

    private void updateGrid(List<AgentInfo> agentInfos) {
        grid.setItems(agentInfos);
        if (agentInfos.isEmpty()) {
            Notification.show("No agents found");
        } else {
            Notification.show("Loaded " + agentInfos.size() + " agents");
        }
    }
    
    private void showLoading(boolean show) {
        loadingIndicator.setVisible(show);
        grid.setEnabled(!show);
    }

	@Override
	public void afterNavigation(AfterNavigationEvent event) {
		
		// Load data on view initialization
        reloadData();
        updateLlmStatus();
        startLlmStatusAutoRefresh();
		
	}

    private void updateLlmStatus() {
        try {
            List<LLMStatus> statuses = llmStatusService.getCurrentStatus();
            if (!statuses.isEmpty()) {
                llmStatusBadge.updateStatus(statuses.get(0).status());
            }
        } catch (Exception e) {
            llmStatusBadge.updateStatus(com.hdekker.ai_workflow.rest.dto.AdapterStatus.UNKNOWN);
        }
    }

    private void startLlmStatusAutoRefresh() {
        if (refreshScheduler != null) {
            refreshScheduler.shutdownNow();
        }
        refreshScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "agent-list-llm-refresh");
            t.setDaemon(true);
            return t;
        });
        refreshScheduler.scheduleAtFixedRate(() -> {
            com.vaadin.flow.component.UI.getCurrent().access(() -> updateLlmStatus());
        }, 15, 15, TimeUnit.SECONDS);
    }

    @Override
    protected void onDetach(com.vaadin.flow.component.DetachEvent detachEvent) {
        if (refreshScheduler != null) {
            refreshScheduler.shutdownNow();
            refreshScheduler = null;
        }
        super.onDetach(detachEvent);
    }
}

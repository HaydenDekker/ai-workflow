package com.hdekker.ai_workflow.adapter.inbound.ui.view;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hdekker.ai_workflow.adapter.inbound.rest.dto.AgentInfo;
import com.hdekker.ai_workflow.adapter.inbound.rest.dto.LLMStatus;
import com.hdekker.ai_workflow.adapter.inbound.ui.component.AgentCreationDialog;
import com.hdekker.ai_workflow.adapter.inbound.ui.component.AgentDetailDialog;
import com.hdekker.ai_workflow.adapter.inbound.ui.component.LlmStatusBadge;
import com.hdekker.ai_workflow.adapter.inbound.ui.service.AgentInfoService;
import com.hdekker.ai_workflow.application.agent.AgentStatusService;
import com.hdekker.ai_workflow.application.agent.port.LLMStatusRepository.LLMStatusRecord;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
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
 * Main dashboard view displaying AgentInfo in a grid layout.
 */
@Route("agents")
@PageTitle("Agent List")
public class AgentListView extends VerticalLayout implements AfterNavigationObserver {
    private static final Logger log = LoggerFactory.getLogger(AgentListView.class);
    final Grid<AgentInfo> grid;  // package-private for browserless test access
    private final AgentInfoService agentInfoService;
    private final AgentStatusService llmStatusService;
    private final ProgressBar loadingIndicator;
    private final LlmStatusBadge llmStatusBadge;
    private ScheduledExecutorService refreshScheduler;

    @Autowired
    public AgentListView(AgentInfoService agentInfoService, AgentStatusService llmStatusService) {
        this.agentInfoService = agentInfoService;
        this.llmStatusService = llmStatusService;
        
        // Setup layout to fill viewport with padding
        addClassName("main-layout");
        setSizeFull();
        setPadding(false);
        setSpacing(false);
        getElement().getStyle().setPadding("16px 32px");
        getElement().getStyle().setBackgroundColor("#f5f5f5");
        
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
        gridContainer.setMargin(false);
        gridContainer.getElement().getStyle().setBackgroundColor("white");
        gridContainer.getElement().getStyle().setBorderRadius("8px");
        gridContainer.getElement().getStyle().setPadding("16px 24px");
        
        grid = new Grid<AgentInfo>();
        grid.setWidthFull();
        grid.setHeightFull();
        grid.setMinHeight("500px");
        // Enable row click to open detail dialog
        grid.addThemeVariants(GridVariant.LUMO_NO_BORDER);
        grid.addItemClickListener(e -> {
            AgentDetailDialog dialog = new AgentDetailDialog(
                    agentInfoService,
                    e.getItem(),
                    updatedInfo ->  grid.getUI().orElseThrow().access(() -> reloadData()),    // onSave: refresh grid
                    deletedId -> {
                    	log.info("reloading");
                    	grid.getUI().orElseThrow().access(() -> {reloadData();}); 
                    }
                    	
                          // onDelete: refresh grid
            );
            dialog.open();
        });
        
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
            
        grid.addColumn(agent -> agent.definition() != null && agent.definition().targetDirectory() != null 
            ? agent.definition().targetDirectory() : "N/A")
            .setHeader("Target Dir")
            .setAutoWidth(true)
            .setSortable(true);
            
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

        // Actions column: Refresh button per row (component column)
        grid.addComponentColumn(agent -> {
            Button refreshBtn = new Button(new Icon(VaadinIcon.REFRESH));
            refreshBtn.addClassName("agent-refresh-btn");
            refreshBtn.getElement().setAttribute("title", "Refresh agent (full rescan)");
            refreshBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
            refreshBtn.addClickListener(e -> refreshAgent(agent));
            return refreshBtn;
        }).setHeader("Actions").setAutoWidth(true);
        
        // Add grid to its container
        gridContainer.add(grid);
        
        // Add loading indicator
        add(loadingIndicator);
        
        // Make grid container expand to fill remaining space
        gridContainer.setSizeFull();
        gridContainer.setFlexGrow(1, gridContainer);
        
        // Add components to layout
        add(headerLayout);
        add(gridContainer);
        add(new Hr());
        
        // Add navigation and refresh buttons
        Button refreshButton = new Button("Refresh", event -> reloadData());
        Button createButton = new Button("New Agent", event -> {
            AgentCreationDialog dialog = new AgentCreationDialog(agentInfoService);
            dialog.addOpenedChangeListener(e -> {
                if (!e.isOpened()) {
                    reloadData();
                }
            });
            dialog.open();
        });
        
        HorizontalLayout buttonLayout = new HorizontalLayout(createButton, refreshButton);
        add(buttonLayout);
    
    }

    void reloadData() {  // package-private for browserless test access
        log.debug("reloadData() called - refreshing agent grid");
        showLoading(true);
        try {
            List<AgentInfo> agentInfos = agentInfoService.getAllAgentInfos().block();
            log.info("Loaded {} agents", agentInfos.size());
            grid.setItems(agentInfos);
        } catch (Exception error) {
            Notification.show("Error loading data: " + error.getMessage());
        } finally {
            showLoading(false);
        }
    }

    private void updateGrid(List<AgentInfo> agentInfos) {
        grid.setItems(agentInfos);
        if (agentInfos.isEmpty()) {
            Notification.show("No agents found");
        } else {
            Notification.show("Loaded " + agentInfos.size() + " agents");
        }
    }

    private void refreshAgent(AgentInfo agent) {
        Notification.show("Refreshing agent: " + agent.id(), 2000, Notification.Position.MIDDLE);
        agentInfoService.refreshAgent(agent.id()).subscribe(
            info -> {
                Notification.show("Agent " + agent.id() + " refreshed", 3000, Notification.Position.MIDDLE);
                reloadData();
            },
            err -> Notification.show("Refresh failed: " + err.getMessage(), 4000, Notification.Position.MIDDLE)
        );
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
            List<LLMStatusRecord> records = llmStatusService.getCurrentStatus();
            if (!records.isEmpty()) {
                com.hdekker.ai_workflow.adapter.inbound.rest.dto.AdapterStatus status =
                        com.hdekker.ai_workflow.adapter.inbound.rest.dto.AdapterStatus.valueOf(records.get(0).status());
                llmStatusBadge.updateStatus(status);
            }
        } catch (Exception e) {
            llmStatusBadge.updateStatus(com.hdekker.ai_workflow.adapter.inbound.rest.dto.AdapterStatus.UNKNOWN);
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

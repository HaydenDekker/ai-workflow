package com.hdekker.ai_workflow.ui.views;

import com.vaadin.flow.router.Route;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.router.AfterNavigationEvent;
import com.vaadin.flow.router.AfterNavigationObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.component.html.Hr;
import com.vaadin.flow.component.notification.Notification;

import com.hdekker.ai_workflow.rest.dto.PipelineInfo;
import com.hdekker.ai_workflow.ui.service.PipelineInfoService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.List;

/**
 * Main dashboard view displaying PipelineInfo in a grid layout.
 */
@Route("")
@PageTitle("Pipeline Grid")
public class PipelineInfoListView extends VerticalLayout implements AfterNavigationObserver {
    private final Grid<PipelineInfo> grid;
    private final PipelineInfoService pipelineInfoService;
    private final ProgressBar loadingIndicator;

    @Autowired
    public PipelineInfoListView(PipelineInfoService pipelineInfoService) {
        this.pipelineInfoService = pipelineInfoService;
        
        // Setup layout with styling
        addClassName("main-layout");
        setPadding(true);
        setSpacing(true);
        
        // Initialize loading indicator
        loadingIndicator = new ProgressBar();
        loadingIndicator.setVisible(false);
        loadingIndicator.setWidth("100%");
        
        // Header
        H2 header = new H2("Pipeline Grid");
        header.addClassName("page-title");
        
        // Grid component with container for styling
        VerticalLayout gridContainer = new VerticalLayout();
        gridContainer.addClassName("grid-container");
        gridContainer.setPadding(false);
        gridContainer.setSpacing(false);
        
        grid = new Grid<PipelineInfo>();
        grid.setWidth("100%");
        grid.setHeight("400px");
        grid.setSizeFull();
        
        // Configure columns with proper sizing and sorting
        grid.addColumn(PipelineInfo::id)
            .setHeader("ID")
            .setAutoWidth(true)
            .setSortable(true);
            
        grid.addColumn(pipeline -> pipeline.agentDefinition() != null ? pipeline.agentDefinition().title() : "N/A")
            .setHeader("Title")
            .setFlexGrow(1)
            .setSortable(true);
            
        grid.addColumn(pipeline -> pipeline.agentDefinition() != null ? pipeline.agentDefinition().agentType() : "N/A")
            .setHeader("Agent Type")
            .setAutoWidth(true)
            .setSortable(true);
            
        grid.addColumn(pipeline -> pipeline.agentDefinition() != null && pipeline.agentDefinition().fileInputRegex() != null 
            ? pipeline.agentDefinition().fileInputRegex() : "N/A")
            .setHeader("File Regex")
            .setFlexGrow(2);
            
        grid.addColumn(PipelineInfo::source)
            .setHeader("Source")
            .setAutoWidth(true)
            .setSortable(true);
            
        grid.addColumn(PipelineInfo::createdAt)
            .setHeader("Created")
            .setAutoWidth(true)
            .setSortable(true);
            
        grid.addColumn(PipelineInfo::active)
            .setHeader("Active")
            .setAutoWidth(true)
            .setSortable(true);
        
        // Add grid to its container
        gridContainer.add(grid);
        
        // Add loading indicator
        add(loadingIndicator);
        
        // Add components to layout
        add(header);
        add(gridContainer);
        add(new Hr());
        
        // Add navigation and refresh buttons
        Button refreshButton = new Button("Refresh", event -> reloadData());
        Button createButton = new Button("New Pipeline", event -> {
            // Placeholder for new pipeline creation
            Notification.show("Create new Pipeline dialog will open here");
        });
        
        HorizontalLayout buttonLayout = new HorizontalLayout(createButton, refreshButton);
        add(buttonLayout);
        
    
    }

    private void reloadData() {
        showLoading(true);
        pipelineInfoService.getAllPipelineInfos()
            .doFinally(signalType -> grid.getUI().get().access(() -> showLoading(false)))
            .subscribe(
                pipelineInfos -> grid.getUI().get().access(() -> updateGrid(pipelineInfos)),
                error -> grid.getUI().get().access(() -> {
                    Notification.show("Error loading data: " + error.getMessage());
                    showLoading(false);
                })
            );
    }

    private void updateGrid(List<PipelineInfo> pipelineInfos) {
        grid.setItems(pipelineInfos);
        if (pipelineInfos.isEmpty()) {
            Notification.show("No pipelines found");
        } else {
            Notification.show("Loaded " + pipelineInfos.size() + " pipelines");
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
		
	}
}
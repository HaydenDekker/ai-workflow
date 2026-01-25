package com.hdekker.ai_workflow.ui.views;

import com.vaadin.flow.router.Route;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.component.html.Hr;
import com.vaadin.flow.component.notification.Notification;

import com.hdekker.ai_workflow.rest.dto.PipelineInfo;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;

/**
 * Main dashboard view displaying PipelineInfo in a grid layout.
 */
@Route("")
@PageTitle("Pipeline Grid")
public class PipelineInfoListView extends VerticalLayout {
    private final Grid<PipelineInfo> grid;

    public PipelineInfoListView() {
        // Setup layout with styling
        addClassName("main-layout");
        setPadding(true);
        setSpacing(true);
        
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
        add(new Paragraph("Load data from API..."));
    }

    private void reloadData() {
        // TODO: Call REST endpoint /api/pipelines
        Notification.show("Refreshing data...");
    }
}
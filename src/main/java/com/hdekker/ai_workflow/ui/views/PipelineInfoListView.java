package com.hdekker.ai_workflow.ui.views;

import com.vaadin.flow.component.Text;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.PageTitle;

/**
 * Main view displaying PipelineInfo objects in a grid layout.
 * This is the default landing page for the application.
 */
@Route("")
@PageTitle("Pipeline Info Grid")
public class PipelineInfoListView extends VerticalLayout {

    public PipelineInfoListView() {
        // Set up the basic layout structure
        setSpacing(true);
        setPadding(true);
        setSizeFull();
        
        // Add main title
        add(new Text("Pipeline Information"));
    }
}
package com.hdekker.ai_workflow.views;

import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

@Route("")
@PageTitle("AI workflow")
public class AIWorkflowHomeView extends VerticalLayout{
	
	/**
	 * 
	 */
	private static final long serialVersionUID = -7768121568061925115L;

	AIWorkflowHomeView(){
		add(new H2("AI Workflow"));
	}

}

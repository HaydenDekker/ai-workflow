package com.hdekker.ai_workflow.views;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;

import com.hdekker.ai_workflow.database.promptresponse.PromptResponseDatabase;
import com.hdekker.ai_workflow.prompt.PromptResponse;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.markdown.Markdown;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.router.AfterNavigationEvent;
import com.vaadin.flow.router.AfterNavigationObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

@Route("")
@PageTitle("AI workflow")
public class AIWorkflowHomeView extends VerticalLayout implements AfterNavigationObserver{
	
	/**
	 * 
	 */
	private static final long serialVersionUID = -7768121568061925115L;

	@Autowired
	PromptResponseDatabase promptResponseDatabase;
	
	Grid<PromptResponse> responseGrid = new Grid<PromptResponse>();
	
	AIWorkflowHomeView(){
		
		setHeightFull(); 
		
		add(new H2("AI Workflow"));
		responseGrid.addColumn((p)-> p.prompt().title()).setHeader("Prompt Title");
		responseGrid.addColumn(PromptResponse::fileName).setHeader("File name");
		responseGrid.setItemDetailsRenderer(new ComponentRenderer<Div, PromptResponse>(Div::new, (c,v)->{
			Markdown md = new Markdown(
					v.prompt().body() + 
					v.file() +
					v.prompt().outputStructure() +
					v.response());
			md.addClassName("markdown-output");
			c.add(md);
		}));
		add(responseGrid);
		
		responseGrid.setHeightFull();
		
	}

	@Override
	public void afterNavigation(AfterNavigationEvent event) {
		List<PromptResponse> list = promptResponseDatabase.responseList();
		responseGrid.setItems(list);
	}
}

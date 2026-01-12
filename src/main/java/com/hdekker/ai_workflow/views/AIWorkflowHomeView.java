package com.hdekker.ai_workflow.views;

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

/**
 *  To provide observability of the agents - later problem.
 *  
 *  Show configured
 *  Allow one-shot tests / replays without affecting filesystem
 *  Show runtime stats of nodes
 *  Show input filter stats
 *  Show output file creation stats
 *  
 */
@Route("")
@PageTitle("AI workflow")
public class AIWorkflowHomeView extends VerticalLayout implements AfterNavigationObserver{
	
	/**
	 * 
	 */
	private static final long serialVersionUID = -7768121568061925115L;
	
	Grid<PromptResponse> responseGrid = new Grid<PromptResponse>();
	
	AIWorkflowHomeView(){
		
		setHeightFull(); 
		
		add(new H2("AI Workflow"));
		responseGrid.addColumn((p)-> p.prompt().title()).setHeader("Prompt Title");
		responseGrid.addColumn(PromptResponse::fileName).setHeader("File name");
		responseGrid.setItemDetailsRenderer(new ComponentRenderer<Div, PromptResponse>(Div::new, (c,v)->{
			Markdown md = new Markdown(
					v.prompt().body() + 
					v.fileContents() +
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
		
	}
}

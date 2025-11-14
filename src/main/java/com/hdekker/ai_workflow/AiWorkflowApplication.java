package com.hdekker.ai_workflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.theme.Theme;

@SpringBootApplication
@Theme("default")
public class AiWorkflowApplication implements AppShellConfigurator {

	private static final long serialVersionUID = -2379665317533773323L;

	public static void main(String[] args) {
		SpringApplication.run(AiWorkflowApplication.class, args);
	}

}

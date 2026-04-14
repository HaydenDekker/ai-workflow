package com.hdekker.ai_workflow;

import org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration;
import org.springframework.ai.model.openai.autoconfigure.OpenAiEmbeddingAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.theme.lumo.Lumo;

@SpringBootApplication(exclude = {OpenAiChatAutoConfiguration.class, OpenAiEmbeddingAutoConfiguration.class})
@com.vaadin.flow.theme.Theme(themeClass = Lumo.class)
public class AiWorkflowApplication implements AppShellConfigurator {

	private static final long serialVersionUID = -2379665317533773323L;

	public static void main(String[] args) {
		SpringApplication.run(AiWorkflowApplication.class, args);
	}

}

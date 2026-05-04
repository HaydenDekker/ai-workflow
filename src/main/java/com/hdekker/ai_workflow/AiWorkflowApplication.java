package com.hdekker.ai_workflow;

import com.hdekker.ai_workflow.config.DataSourceProperties;
import com.hdekker.ai_workflow.observability.ObservabilityProperties;

import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.component.page.Push;
import com.vaadin.flow.theme.lumo.Lumo;
import org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration;
import org.springframework.ai.model.openai.autoconfigure.OpenAiEmbeddingAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(exclude = {OpenAiChatAutoConfiguration.class, OpenAiEmbeddingAutoConfiguration.class, HibernateJpaAutoConfiguration.class}, excludeName = {"org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration", "org.springframework.boot.autoconfigure.jdbc.DataSourceInitializationAutoConfiguration", "org.springframework.ai.model.openai.autoconfigure.OpenAiAudioSpeechAutoConfiguration", "org.springframework.ai.model.openai.autoconfigure.OpenAiAudioTranscriptionAutoConfiguration", "org.springframework.ai.model.openai.autoconfigure.OpenAiImageAutoConfiguration", "org.springframework.ai.model.openai.autoconfigure.OpenAiModerationAutoConfiguration"})
@EnableConfigurationProperties({DataSourceProperties.class, ObservabilityProperties.class})
@EnableScheduling
@Push
@com.vaadin.flow.theme.Theme(themeClass = Lumo.class)
public class AiWorkflowApplication implements AppShellConfigurator {

	private static final long serialVersionUID = -2379665317533773323L;

	public static void main(String[] args) {
		
		SpringApplication.run(AiWorkflowApplication.class, args); 
	}

}

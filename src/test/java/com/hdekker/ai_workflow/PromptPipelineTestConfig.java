package com.hdekker.ai_workflow;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import com.hdekker.ai_workflow.llm.Prompter;

import reactor.core.publisher.Flux;

@Configuration
@Profile(TestProfiles.RESOURCES_TEST_FOLDER)
public class PromptPipelineTestConfig {
	
	String stub = "```json [{\r\n"
			+ "  \"className\": \"LogSubscriberPort\",\r\n"
			+ "  \"compliance\": \"YES\",\r\n"
			+ "  \"principle\": \"Single Responsibility Principle\",\r\n"
			+ "  \"task\": \"Interface definition\",\r\n"
			+ "  \"reason\": \"The interface defines a single responsibility: to provide a subscribe method for a consumer that receives log messages. It doesn't contain any business logic or complex operations.\"\r\n"
			+ "}]```";

	@Bean
	@Primary
	Prompter prompter() {
		return (s) -> Flux.just(stub);
	}
	
}

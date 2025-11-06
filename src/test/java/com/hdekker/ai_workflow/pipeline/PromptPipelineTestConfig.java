package com.hdekker.ai_workflow.pipeline;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import com.hdekker.ai_workflow.TestProfiles;
import com.hdekker.ai_workflow.llm.Prompter;

import reactor.core.publisher.Flux;

@Configuration
@Profile(TestProfiles.RESOURCES_TEST_FOLDER)
public class PromptPipelineTestConfig {
	
	String stub = ""
			+ " Just a simple test response as if its from the raw output of the LLM "
			+ "```json [{\r\n"
			+ "  \"className\": \"LogSubscriberPort\",\r\n"
			+ "  \"compliance\": \"YES\",\r\n"
			+ "  \"principle\": \"Single Responsibility Principle\",\r\n"
			+ "  \"task\": \"Interface definition\",\r\n"
			+ "  \"reason\": \"The interface defines a single responsibility: to provide a subscribe method for a consumer that receives log messages. It doesn't contain any business logic or complex operations.\"\r\n"
			+ "}]```";

	@Bean
	@Primary
	Prompter prompter() {
		return (s, structure) -> Flux.just(stub);
	}
	
}

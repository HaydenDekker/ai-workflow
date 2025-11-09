package com.hdekker.ai_workflow.pipeline;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import com.hdekker.ai_workflow.TestProfiles;
import com.hdekker.ai_workflow.llm.Prompter;
import com.hdekker.ai_workflow.pipeline.domain.PipelinePrompt;
import com.hdekker.ai_workflow.pipeline.domain.PromptTriggerEvent;
import com.hdekker.ai_workflow.prompt.PromptConfiguration;

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
	
	List<PipelinePrompt> testPipelinePromptList(){
		
		return List.of(
				new PipelinePrompt(
						PromptTriggerEvent.FILE_SYS_HASH_CHANGED_EVENT.name(),
						PromptConfiguration.SOLID_COMPLIANCE_PROMPT_TITLE,
						PromptConfiguration.SOLID_COMPLAINCE_PROMPT,
						PromptConfiguration.SOLID_COMPLIANCE_PROMPT_OUTPUT
						),
				new PipelinePrompt(
						PromptTriggerEvent.PROMPT_RESPONSE_EVENT.name() + "_" + PromptConfiguration.SOLID_COMPLIANCE_PROMPT_TITLE,
						PromptConfiguration.PRIORITY_ORDER_PROMPT_TITLE,
						PromptConfiguration.PRIORITY_ORDER_PROMPT,
						PromptConfiguration.PRIORITY_ORDER_PROMPT_OUTPUT
						)
				);
		
	}
	
}

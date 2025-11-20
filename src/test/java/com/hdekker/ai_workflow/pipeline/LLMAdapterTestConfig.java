package com.hdekker.ai_workflow.pipeline;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import com.hdekker.ai_workflow.TestProfiles;
import com.hdekker.ai_workflow.llm.Prompter;

import reactor.core.publisher.Flux;

@Configuration
@Profile(TestProfiles.MOCK_LLM_PROMPT_ADAPTER)
public class LLMAdapterTestConfig {

	@Bean
	@Primary
	public Prompter testPrompter() {
		return (p,c) -> Flux.just(p + " just return the request.");
	}
	
}

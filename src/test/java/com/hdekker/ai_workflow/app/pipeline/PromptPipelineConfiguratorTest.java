package com.hdekker.ai_workflow.app.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.hdekker.ai_workflow.pipeline.PromptPipelineConfiguration;
import com.hdekker.ai_workflow.prompt.PromptResponse;

import reactor.core.publisher.Flux;

public class PromptPipelineConfiguratorTest {
	
	PromptPipelineConfigurator configurator = new PromptPipelineConfigurator();
	
	@Test
	public void givenPipelineWithZeroStages_ExpectEmptyFluxReturned() {
		
		List<Flux<PromptResponse>> flux = configurator.configure(List.of());
		
		assertThat(flux)
			.hasSize(0);
		
	}

}

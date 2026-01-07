package com.hdekker.ai_workflow.app.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.hdekker.ai_workflow.TestData;
import com.hdekker.ai_workflow.pipeline.PromptPipelineConfiguration;
import com.hdekker.ai_workflow.pipeline.domain.PipelinePrompt;
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
	
	void triggerFileInputEvent() {
		
	}
	
	@Test
	public void givenPipelineWithSingleStage_ExpectSingleFluxReturned() {
		
		PipelinePrompt pp = TestData.basicPrompt();
		
		List<Flux<PromptResponse>> flux = configurator.configure(List.of(pp));
		
		assertThat(flux)
			.hasSize(1);
		
		Flux<PromptResponse> promptFlux = flux.get(0);
		
		PromptResponse pr = promptFlux.doOnSubscribe(s-> triggerFileInputEvent())
			.blockFirst(Duration.ofSeconds(3));
		
		assertThat(pr.prompt())
			.isEqualTo(pp);
			
		
	}

}

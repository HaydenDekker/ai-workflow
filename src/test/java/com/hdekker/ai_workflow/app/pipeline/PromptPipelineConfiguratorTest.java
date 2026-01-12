package com.hdekker.ai_workflow.app.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.hdekker.ai_workflow.TestData;
import com.hdekker.ai_workflow.files.FileHash;
import com.hdekker.ai_workflow.files.FileHistory;
import com.hdekker.ai_workflow.files.domain.FileMetadata;
import com.hdekker.ai_workflow.llm.Prompter;
import com.hdekker.ai_workflow.pipeline.domain.PipelinePrompt;
import com.hdekker.ai_workflow.prompt.PromptResponse;

import reactor.core.publisher.Flux;

/***
 *  To take the users list of graph structures and initialise
 *  the edges in memory.
 * 
 */
public class PromptPipelineConfiguratorTest {
	
	PromptPipelineConfigurator configurator;
	
	String expectedMockResult = "This is the expected result";
	
	@BeforeEach
	public void init() {
		
		String mockFileBody = "This is an example file input body";
		
		FileHistory fh = new FileHistory(
				new FileMetadata(
						"/config/doco.txt", 
						mockFileBody,
						FileHash.hash(mockFileBody)), 
					Optional.empty());
		
		Prompter prompter = (s) -> Flux.just(expectedMockResult);
		
		configurator = new PromptPipelineConfigurator(Flux.just(fh), prompter);
	
	}
	
	@Test
	public void givenPipelineWithZeroStages_ExpectEmptyFluxReturned() {
		
		List<Flux<PromptResponse>> flux = configurator.configure(List.of());
		
		assertThat(flux)
			.hasSize(0);
		
	}
	
	@Test
	public void givenPipelineWithSingleStage_ExpectSingleFluxReturned() {
		
		PipelinePrompt pp = TestData.basicPrompt();
		
		List<Flux<PromptResponse>> flux = configurator.configure(List.of(pp));
		
		assertThat(flux)
			.hasSize(1);
		
		Flux<PromptResponse> promptFlux = flux.get(0);
		
		PromptResponse pr = promptFlux.blockFirst(Duration.ofSeconds(3));
		
		assertThat(pr.prompt())
			.isEqualTo(pp);
		
		assertThat(pr.response())
			.isEqualTo(expectedMockResult);
		
	}

}

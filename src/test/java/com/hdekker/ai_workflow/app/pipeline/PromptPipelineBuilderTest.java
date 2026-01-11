package com.hdekker.ai_workflow.app.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import com.hdekker.ai_workflow.TestData;
import com.hdekker.ai_workflow.llm.Prompter;
import com.hdekker.ai_workflow.pipeline.LLMAdapter;
import com.hdekker.ai_workflow.pipeline.SplittableStrategy;
import com.hdekker.ai_workflow.pipeline.domain.PipelinePrompt;
import com.hdekker.ai_workflow.prompt.PromptRequest;
import com.hdekker.ai_workflow.prompt.PromptResponse;

import reactor.core.publisher.Flux;

public class PromptPipelineBuilderTest {
	
	Flux<PromptRequest> fileInputFlux;
	Prompter prompter;
	
	@Test
	public void givenFileMatchingPromptInputRegex_PipelineFilterPassesFile()  {
		
		PipelinePrompt pp = TestData.basicPrompt();
		PromptResponse basicResponse = TestData.basicResponse();
		Consumer<PromptResponse> persister = (p) -> {};
		fileInputFlux = Flux.just(TestData.basicRequest(TestData.fileNameStub));
		
		prompter = (prompt) -> {
			return Flux.just(basicResponse.response());
		};
		
		LLMAdapter adapter = flux->flux.flatMap(fpe-> 
		prompter.call(pp.body() + "\n\r" + "```code" + fpe.file() + "\n\r" + "```" + "\n\r" + pp.outputStructure())
			.reduce((a,b)-> a+b)
			.map(s-> new PromptResponse(pp, fpe.fileURL(), fpe.file(), s)));
		;
		Flux<PromptResponse> pipeline = PromptPipelineBuilder.<PromptRequest, PromptResponse> instance()
			.withTrigger(fileInputFlux)
			.prompting(adapter::call)
			.persist(persister)
			.split(SplittableStrategy.noSPLT())
			.build();
		
		PromptResponse resp = pipeline.blockFirst();
		
		assertThat(resp.fileName())
			.isEqualTo(basicResponse.fileName());
		
	}
	
	@Test
	public void givenFileNotMatchingPromptInputRegex_ExpectFilterBlocksFile() {
		
		PipelinePrompt pp = TestData.basicPrompt();
		PromptResponse basicResponse = TestData.basicResponse();
		Consumer<PromptResponse> persister = (p) -> {};
		PromptRequest basicRequest = TestData.basicRequest(TestData.fileNameStub);
		
		fileInputFlux = Flux.just(basicRequest)
					.filter(pr-> pp.inputRegexMatches(pr.fileURL()));
		
		prompter = (prompt) -> {
			return Flux.just(basicResponse.response());
		};
		
		LLMAdapter adapter = flux->flux.flatMap(fpe-> 
		prompter.call(pp.body() + "\n\r" + "```code" + fpe.file() + "\n\r" + "```" + "\n\r" + pp.outputStructure())
			.reduce((a,b)-> a+b)
			.map(s-> new PromptResponse(pp, fpe.fileURL(), fpe.file(), s)));
		;
		Flux<PromptResponse> pipeline = PromptPipelineBuilder.<PromptRequest, PromptResponse> instance()
			.withTrigger(fileInputFlux)
			.prompting(adapter::call)
			.persist(persister)
			.split(SplittableStrategy.noSPLT())
			.build();
		
		PromptResponse resp = pipeline.blockFirst();
		
		assertThat(resp)
			.isNull();
		
	}


}

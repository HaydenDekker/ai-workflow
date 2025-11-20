package com.hdekker.ai_workflow.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.hdekker.ai_workflow.TestProfiles;
import com.hdekker.ai_workflow.llm.GenericPromptCaller;
import com.hdekker.ai_workflow.pipeline.domain.PipelinePrompt;
import com.hdekker.ai_workflow.prompt.PromptRequest;
import com.hdekker.ai_workflow.prompt.PromptResponse;

import reactor.core.publisher.Flux;

@SpringBootTest
@ActiveProfiles({
	TestProfiles.MOCK_LLM_PROMPT_ADAPTER,
	TestProfiles.RESOURCES_TEST_FOLDER
	})
public class LLMAdapterTest {
	
	@Autowired
	GenericPromptCaller genericPromptCaller;
	
	@Test
	public void reducerAdapterCombinesPreviousPrompt() {
		
		LLMReducerAdapter llmReducerAdapter = new LLMReducerAdapter(genericPromptCaller);
		
		PipelinePrompt pp = new PipelinePrompt(
				"Not Relevant",  // TODO perhaps the API object needs to be different from the pipeline object 
				"RESPONSE_REDUCTION_TEST", 
				"REDUCTION_NOT_USED",
				"Pretend to decide if the content is relevant and append it to the existing overview if needed. Really a mock will just append.", 
				"Neat and tidy. The mock will do the work.");
		PromptRequest pr = new PromptRequest(pp, "Test one", "/dont/care");
		PromptRequest pr2 = new PromptRequest(pp, "Test two", "/dont/care");
		
		Flux<PromptResponse> resp = llmReducerAdapter.call(Flux.just(pr, pr2));
		
		List<PromptResponse> l = resp.collectList()
			.block();
		
		assertThat(l)
			.hasSize(2);
		
		assertThat(l.get(1).file())
			.contains("Test one");
		
	}

}

package com.hdekker.ai_workflow.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.hdekker.ai_workflow.TestData;
import com.hdekker.ai_workflow.TestProfiles;
import com.hdekker.ai_workflow.llm.Prompter;
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
	Prompter prompter;
	
	@Test
	public void reducerAdapterCombinesPreviousPrompt() {
		
		LLMReducerAdapter llmReducerAdapter = new LLMReducerAdapter(prompter);
		
		PipelinePrompt pp = TestData.basicPrompt();
		PromptRequest pr = new PromptRequest(pp, "Test one", "/dont/care");
		PromptRequest pr2 = new PromptRequest(pp, "Test two", "/dont/care");
		
		Flux<PromptResponse> resp = llmReducerAdapter.call(Flux.just(pr, pr2));
		
		List<PromptResponse> l = resp.collectList()
			.block();
		
		assertThat(l)
			.hasSize(2);
		
		assertThat(l.get(1).fileContents())
			.contains("Test one");
		
	}

}

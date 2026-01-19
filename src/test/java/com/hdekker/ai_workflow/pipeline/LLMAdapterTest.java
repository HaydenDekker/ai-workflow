package com.hdekker.ai_workflow.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.hdekker.ai_workflow.TestData;
import com.hdekker.ai_workflow.TestProfiles;
import com.hdekker.ai_workflow.pipeline.domain.AgentDefinition;
import com.hdekker.ai_workflow.pipeline.llmadapter.LLMReducerAdapter;
import com.hdekker.ai_workflow.prompt.PromptRequest;
import com.hdekker.ai_workflow.prompt.PromptResponse;

import reactor.core.publisher.Flux;

@SpringBootTest
@ActiveProfiles({
	TestProfiles.RESOURCES_TEST_FOLDER
	})
public class LLMAdapterTest {
	
	@Autowired
	ChatClient chatClient;

	@Test
	public void reducerAdapterCombinesPreviousPrompt() {

		AgentDefinition pp = TestData.basicPrompt();
		LLMReducerAdapter llmReducerAdapter = new LLMReducerAdapter(chatClient,pp);
		
		PromptRequest pr = new PromptRequest("Test one", "/dont/care");
		PromptRequest pr2 = new PromptRequest("Test two", "/dont/care");
		
		Flux<PromptResponse> resp = llmReducerAdapter.call(Flux.just(pr, pr2));
		
		List<PromptResponse> l = resp.collectList()
			.block();
		
		assertThat(l)
			.hasSize(2);
		
		assertThat(l.get(1).fileContents())
			.contains("Test one");
		
	}

}

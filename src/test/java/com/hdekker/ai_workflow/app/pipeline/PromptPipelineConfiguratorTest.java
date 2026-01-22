package com.hdekker.ai_workflow.app.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec;
import org.springframework.ai.chat.client.ChatClient.StreamResponseSpec;

import com.hdekker.ai_workflow.TestData;
import com.hdekker.ai_workflow.files.FileHash;
import com.hdekker.ai_workflow.files.FileHistory;
import com.hdekker.ai_workflow.files.domain.FileMetadata;
import com.hdekker.ai_workflow.pipeline.domain.AgentDefinition;
import com.hdekker.ai_workflow.prompt.PromptResponse;

import reactor.core.publisher.Flux;
import static org.mockito.Mockito.*;

/***
 *  To take the users list of graph structures and initialise
 *  the edges in memory.
 * 
 */
public class PromptPipelineConfiguratorTest {
	
	PromptPipelineConfigurator configurator;
	
	String expectedMockResult = "This is the expected result";
	
	Boolean persistCalled = false;
	
	@BeforeEach
	public void init() {
		
		String mockFileBody = "This is an example file input body";
		
		FileHistory fh = new FileHistory(
				new FileMetadata(
						"/config/doco.txt", 
						mockFileBody,
						FileHash.hash(mockFileBody)), 
					Optional.empty());
		
		ChatClient chatClient = mock(ChatClient.class);
		ChatClientRequestSpec requestSpec = mock(ChatClientRequestSpec.class);
		StreamResponseSpec streamSpec = mock(StreamResponseSpec.class);

		when(chatClient.prompt(anyString())).thenReturn(requestSpec);
		when(requestSpec.stream()).thenReturn(streamSpec);
		when(streamSpec.content()).thenReturn(Flux.just(expectedMockResult));

		Consumer<PromptResponse> persister = (pr) -> {
			persistCalled = true;
		};

		configurator = new PromptPipelineConfigurator(
				Flux.just(fh),
				chatClient,
				persister);
	
	}

	
	@Test
	public void givenPipelineWithSingleStage_ExpectSingleFluxReturned() {
		
		AgentDefinition pp = TestData.basicPrompt();
		
		Flux<PromptResponse> flux = configurator.configure(pp);
		
		PromptResponse pr = flux.blockFirst(Duration.ofSeconds(3));
		
		assertThat(pr.prompt())
			.isEqualTo(pp);
		
		assertThat(pr.response())
			.isEqualTo(expectedMockResult);
		
		assertThat(persistCalled)
			.isTrue();
		
	}

}

package com.hdekker.ai_workflow.app.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec;
import org.springframework.ai.chat.client.ChatClient.StreamResponseSpec;
import org.springframework.ai.chat.model.ChatResponse;
import com.hdekker.ai_workflow.TestData;
import com.hdekker.ai_workflow.pipeline.LLMAdapter;
import reactor.core.publisher.Flux;
import static org.mockito.Mockito.*;
import com.hdekker.ai_workflow.pipeline.SplittableStrategy;
import com.hdekker.ai_workflow.pipeline.domain.AgentDefinition;
import com.hdekker.ai_workflow.prompt.PromptRequest;
import com.hdekker.ai_workflow.prompt.PromptResponse;

import reactor.core.publisher.Flux;

public class PromptPipelineBuilderTest {
	
	Flux<PromptRequest> fileInputFlux;
	ChatClient chatClient;
	
	String outputFilename = "";
	
	@Test
	public void givenFileMatchingPromptInputRegex_PipelineFilterPassesFile()  {
		
		AgentDefinition pp = TestData.basicPrompt();
		PromptResponse basicResponse = TestData.basicResponse();
		Consumer<PromptResponse> persister = (p) -> {
			outputFilename = p.createOutputFileName();
		};
		fileInputFlux = Flux.just(TestData.basicRequest(TestData.fileNameStub));

		ChatClient mockChatClient = mock(ChatClient.class);
		ChatClientRequestSpec requestSpec = mock(ChatClientRequestSpec.class);
		StreamResponseSpec streamSpec = mock(StreamResponseSpec.class);

		when(mockChatClient.prompt(anyString())).thenReturn(requestSpec);
		when(requestSpec.stream()).thenReturn(streamSpec);
		when(streamSpec.content()).thenReturn(Flux.just(basicResponse.response()));

		this.chatClient = mockChatClient;

		LLMAdapter adapter = flux->flux.flatMap(fpe->
		chatClient.prompt(pp.body() + "\n\r" + "```code" + fpe.file() + "\n\r" + "```" + "\n\r" + pp.outputStructure())
			.stream()
			.content()
			.reduce((a,b)-> a+b)
			.map(s-> new PromptResponse(pp, fpe.fileURL(), fpe.file(), s)));
		;
		Flux<PromptResponse> pipeline = PromptPipelineBuilder.instance()
			.withDefinition(pp)
			.withTrigger(fileInputFlux)
			.prompting(adapter::call)
			.persist(persister)
			.split(SplittableStrategy.noSPLT())
			.build();
		
		PromptResponse resp = pipeline.blockFirst();
		
		assertThat(resp.fileName())
			.isEqualTo(basicResponse.fileName());
		
		assertThat(outputFilename)
			.isEqualTo("output/input-file.txt");
		
	}

	
	@Test
	public void givenFileNotMatchingPromptInputRegex_ExpectFilterBlocksFile() {
		
		AgentDefinition pp = TestData.basicPrompt();
		PromptResponse basicResponse = TestData.basicResponse();
		Consumer<PromptResponse> persister = (p) -> {};
		PromptRequest basicRequest = TestData.basicRequest("diff-type.json");
		
		fileInputFlux = Flux.just(basicRequest);

		chatClient = Mockito.mock(ChatClient.class);
		ChatClientRequestSpec requestSpec = Mockito.mock(ChatClientRequestSpec.class);
		StreamResponseSpec streamSpec = Mockito.mock(StreamResponseSpec.class);

		Mockito.when(chatClient.prompt(Mockito.anyString())).thenReturn(requestSpec);
		Mockito.when(requestSpec.stream()).thenReturn(streamSpec);
		Mockito.when(streamSpec.content()).thenReturn(Flux.just(basicResponse.response()));

		LLMAdapter adapter = flux->flux.flatMap(fpe->
		chatClient.prompt(pp.body() + "\n\r" + "```code" + fpe.file() + "\n\r" + "```" + "\n\r" + pp.outputStructure())
			.stream()
			.content()
			.reduce((a,b)-> a+b)
			.map(s-> new PromptResponse(pp, fpe.fileURL(), fpe.file(), s)));
		;
		Flux<PromptResponse> pipeline = PromptPipelineBuilder.instance()
			.withDefinition(pp)
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

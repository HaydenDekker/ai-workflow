package com.hdekker.ai_workflow.app.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import com.hdekker.ai_workflow.TestData;
import com.hdekker.ai_workflow.pipeline.LLMAdapter;
import reactor.core.publisher.Flux;
import com.hdekker.ai_workflow.pipeline.SplittableStrategy;
import com.hdekker.ai_workflow.domain.agent.AgentDefinition;
import com.hdekker.ai_workflow.domain.prompt.PromptRequest;
import com.hdekker.ai_workflow.domain.prompt.PromptResponse;
import com.hdekker.ai_workflow.test.pipeline.mock.ChatClientMockBuilder;

public class AgentBuilderTest {
	
	Flux<PromptRequest> fileInputFlux;
	ChatClient chatClient;
	
	String outputFilename = "";
	
	@Test
	public void givenFileMatchingAgentInputRegex_AgentFilterPassesFile()  {
		
		AgentDefinition agent = TestData.basicPrompt();
		PromptResponse basicResponse = TestData.basicResponse();
		Consumer<PromptResponse> persister = (p) -> {
			outputFilename = p.createOutputFileName();
		};
		fileInputFlux = Flux.just(TestData.basicRequest(TestData.fileNameStub));

		this.chatClient = ChatClientMockBuilder.createMock(basicResponse.response());

		LLMAdapter adapter = flux->flux.flatMap(fpe->
		chatClient.prompt(agent.body() + "\n\r" + "```code" + fpe.file() + "\n\r" + "```" + "\n\r" + agent.outputStructure())
			.stream()
			.content()
			.reduce((a,b)-> a+b)
			.map(s-> new PromptResponse(agent, fpe.fileURL(), fpe.file(), s)));
		
		Flux<PromptResponse> flux = AgentBuilder.instance()
			.withDefinition(agent)
			.withTrigger(fileInputFlux)
			.prompting(adapter::call)
			.persist(persister)
			.split(SplittableStrategy.noSPLT())
			.build();
		
		PromptResponse resp = flux.blockFirst();
		
		assertThat(resp.fileName())
			.isEqualTo(basicResponse.fileName());
		
		assertThat(outputFilename)
			.isEqualTo("output/input-file.txt");
		
	}


	@Test
	public void givenFileNotMatchingAgentInputRegex_ExpectFilterBlocksFile() {
		
		AgentDefinition agent = TestData.basicPrompt();
		PromptResponse basicResponse = TestData.basicResponse();
		Consumer<PromptResponse> persister = (p) -> {};
		PromptRequest basicRequest = TestData.basicRequest("diff-type.json");
		
		fileInputFlux = Flux.just(basicRequest);

		chatClient = ChatClientMockBuilder.createMock(basicResponse.response());

		LLMAdapter adapter = flux->flux.flatMap(fpe->
		chatClient.prompt(agent.body() + "\n\r" + "```code" + fpe.file() + "\n\r" + "```" + "\n\r" + agent.outputStructure())
			.stream()
			.content()
			.reduce((a,b)-> a+b)
			.map(s-> new PromptResponse(agent, fpe.fileURL(), fpe.file(), s)));
		
		Flux<PromptResponse> flux = AgentBuilder.instance()
			.withDefinition(agent)
			.withTrigger(fileInputFlux)
			.prompting(adapter::call)
			.persist(persister)
			.split(SplittableStrategy.noSPLT())
			.build();
		
		PromptResponse resp = flux.blockFirst();
		
		assertThat(resp)
			.isNull();
		
	}


}

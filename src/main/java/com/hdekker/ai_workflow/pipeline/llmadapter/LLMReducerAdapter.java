package com.hdekker.ai_workflow.pipeline.llmadapter;

import org.springframework.ai.chat.client.ChatClient;
import com.hdekker.ai_workflow.pipeline.LLMAdapter;
import com.hdekker.ai_workflow.pipeline.domain.AgentDefinition;
import com.hdekker.ai_workflow.prompt.PromptRequest;
import com.hdekker.ai_workflow.prompt.PromptResponse;

import reactor.core.publisher.Flux;

public class LLMReducerAdapter implements LLMAdapter {

	final ChatClient chatClient;
	AgentDefinition agentDefinition;

	public LLMReducerAdapter(ChatClient chatClient, AgentDefinition agentDefinition){
		this.chatClient = chatClient;
		this.agentDefinition = agentDefinition;
	}

	String latestResponse = "";
	
	@Override
	public Flux<PromptResponse> call(Flux<PromptRequest> request) {
		
		return request.concatMap(pp->{
			
			String fileContent = "\n\r\n" + (latestResponse.isEmpty() ? "" : "Current Snapshot: \r\n\r\n" + latestResponse)
                                 + (latestResponse.isEmpty() ? "" : "\n\r\n")
                                 + "New aspect for analysis: \r\n\r\n" + pp.file();

			return chatClient.prompt(agentDefinition.body() + "\n\r" + "```code" + fileContent + "\n\r" + "```" + "\n\r" + agentDefinition.outputStructure())
				.stream()
				.content()
				.reduce((a,b)-> a+b)
				.map(s-> new PromptResponse(agentDefinition, pp.fileURL(), fileContent, s))
					.doOnNext(p -> {
						latestResponse = p.response();
					});
			
		});
	}
	
	

}

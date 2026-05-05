package com.hdekker.ai_workflow.app.pipeline;

import java.util.function.Consumer;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hdekker.ai_workflow.adapter.outbound.llm.LLMAdapter;
import com.hdekker.ai_workflow.adapter.outbound.llm.LLMAdapterFactory;
import com.hdekker.ai_workflow.application.pipeline.SplittableStrategy;
import com.hdekker.ai_workflow.domain.agent.AgentDefinition;
import com.hdekker.ai_workflow.domain.file.FileHistory;
import com.hdekker.ai_workflow.domain.prompt.PromptResponse;

import org.springframework.ai.chat.client.ChatClient;
import reactor.core.publisher.Flux;

public class AgentConfigurator {
	
	Logger log = LoggerFactory.getLogger(AgentConfigurator.class);
	
	final Flux<FileHistory> fileInputFlux;
	ChatClient chatClient;
	Consumer<PromptResponse> persister;

	public AgentConfigurator(
			Flux<FileHistory> fileInputFlux,
			ChatClient chatClient,
			Consumer<PromptResponse> persister
			){
		this.fileInputFlux = fileInputFlux;
		this.chatClient = chatClient;
		this.persister = persister;
	}

	public Flux<PromptResponse> configure(AgentDefinition agentDefinition) {
		
			LLMAdapter adapter = LLMAdapterFactory.create(chatClient, agentDefinition);
			
			return AgentBuilder.instance()
					.withDefinition(agentDefinition)
					.withTrigger(fileInputFlux
							.map(fh-> fh.to()))
					.prompting(adapter::call)
					.persist(persister)
					.split(SplittableStrategy.noSPLT())
					.build();

			
	}

}

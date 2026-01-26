package com.hdekker.ai_workflow.app.pipeline;

import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;

import com.hdekker.ai_workflow.files.FileHistory;
import com.hdekker.ai_workflow.pipeline.LLMAdapter;
import com.hdekker.ai_workflow.pipeline.SplittableStrategy;
import com.hdekker.ai_workflow.pipeline.domain.AgentDefinition;
import com.hdekker.ai_workflow.pipeline.llmadapter.LLMAdapterFactory;
import com.hdekker.ai_workflow.prompt.PromptResponse;

import reactor.core.publisher.Flux;

public class PromptPipelineConfigurator {
	
	Logger log = LoggerFactory.getLogger(PromptPipelineConfigurator.class);
	
	final Flux<FileHistory> fileInputFlux;
	ChatClient chatClient;
	Consumer<PromptResponse> persister;

	public PromptPipelineConfigurator(
			Flux<FileHistory> fileInputFlux,
			ChatClient chatClient,
			Consumer<PromptResponse> persister
			){
		this.fileInputFlux = fileInputFlux;
		this.chatClient = chatClient;
		this.persister = persister;
	}

	public Flux<PromptResponse> configure(AgentDefinition agentDefintion) {
		
			LLMAdapter adapter = LLMAdapterFactory.create(chatClient, agentDefintion);
			
			return PromptPipelineBuilder.instance()
					.withDefinition(agentDefintion)
					.withTrigger(fileInputFlux
							.map(fh-> fh.to()))
					.prompting(adapter::call)
					.persist(persister)
					.split(SplittableStrategy.noSPLT())
					.build();

			
	}

}

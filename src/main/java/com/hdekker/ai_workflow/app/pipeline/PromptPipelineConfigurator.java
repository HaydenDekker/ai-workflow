package com.hdekker.ai_workflow.app.pipeline;

import java.util.List;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;

import com.hdekker.ai_workflow.files.FileHistory;
import com.hdekker.ai_workflow.pipeline.LLMAdapter;
import com.hdekker.ai_workflow.pipeline.SplittableStrategy;
import com.hdekker.ai_workflow.pipeline.domain.AgentDefinition;
import com.hdekker.ai_workflow.pipeline.llmadapter.LLMReducerAdapter;
import com.hdekker.ai_workflow.pipeline.llmadapter.SplitterLLMAdapter;
import com.hdekker.ai_workflow.prompt.PromptResponse;

import reactor.core.publisher.Flux;

public class PromptPipelineConfigurator {
	
	Logger log = LoggerFactory.getLogger(PromptPipelineConfigurator.class);
	
	final Flux<FileHistory> fileInputFlux;
	// TODO more a llm adapter.
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

	public List<Flux<PromptResponse>> configure(List<AgentDefinition> promptChain) {
		
		if(promptChain.size()==0) {
			log.warn("Empty prompt list, dev: consider adding validation to interface.");
			return List.of();
		}
		
		return promptChain.stream()
			.map(pp->{
				
				LLMAdapter gp = flux->flux.flatMap(fpe->
					chatClient.prompt(pp.body() + "\n\r" + "```code" + fpe.file() + "\n\r" + "```" + "\n\r" + pp.outputStructure())
						.stream()
						.content()
						.reduce((a,b)-> a+b)
						.map(s-> new PromptResponse(pp, fpe.fileURL(), fpe.file(), s))
				);
		
			
				LLMAdapter adapter = (pp.agentType()!=null && pp.agentType().equals("Reduction")) ?
						new LLMReducerAdapter(chatClient, pp):
						(pp.agentType()!=null && pp.agentType().equals("Split")) ?
						new SplitterLLMAdapter(chatClient, pp):
							gp;
				
				return PromptPipelineBuilder.instance()
						.withDefinition(pp)
						.withTrigger(fileInputFlux
								.map(fh-> fh.to()))
						.prompting(adapter::call)
						.persist(persister)
						.split(SplittableStrategy.noSPLT())
						.build();
		
			})
			.toList();
			
	}

}

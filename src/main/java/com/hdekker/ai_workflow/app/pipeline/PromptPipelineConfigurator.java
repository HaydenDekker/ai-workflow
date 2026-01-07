package com.hdekker.ai_workflow.app.pipeline;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hdekker.ai_workflow.pipeline.domain.PipelinePrompt;
import com.hdekker.ai_workflow.prompt.PromptResponse;

import reactor.core.publisher.Flux;

public class PromptPipelineConfigurator {
	
	Logger log = LoggerFactory.getLogger(PromptPipelineConfigurator.class);
	
	public PromptPipelineConfigurator(){
		
	}

	public List<Flux<PromptResponse>> configure(List<PipelinePrompt> promptChain) {
		
		if(promptChain.size()==0) {
			log.warn("Empty prompt list, dev: consider adding validation to interface.");
			return List.of();
		}
		
		return promptChain.stream()
			.map(pp->{
				return Flux.just(
						new PromptResponse(null, null, null, null));
			})
			.toList();
			
	}

}

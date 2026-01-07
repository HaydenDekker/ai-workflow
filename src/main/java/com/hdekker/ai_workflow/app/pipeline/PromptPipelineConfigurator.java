package com.hdekker.ai_workflow.app.pipeline;

import java.util.List;

import com.hdekker.ai_workflow.pipeline.domain.PipelinePrompt;
import com.hdekker.ai_workflow.prompt.PromptResponse;

import reactor.core.publisher.Flux;

public class PromptPipelineConfigurator {
	
	public PromptPipelineConfigurator(){
		
	}

	public List<Flux<PromptResponse>> configure(List<PipelinePrompt> promtChain) {
		return List.of();
	}

}

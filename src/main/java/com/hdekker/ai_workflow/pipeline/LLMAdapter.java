package com.hdekker.ai_workflow.pipeline;

import com.hdekker.ai_workflow.prompt.PromptRequest;
import com.hdekker.ai_workflow.prompt.PromptResponse;

import reactor.core.publisher.Flux;

public interface LLMAdapter {
	
	Flux<PromptResponse> call(PromptRequest request);

}

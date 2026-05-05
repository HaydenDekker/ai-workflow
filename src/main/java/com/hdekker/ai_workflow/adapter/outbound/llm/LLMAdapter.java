package com.hdekker.ai_workflow.adapter.outbound.llm;

import com.hdekker.ai_workflow.domain.prompt.PromptRequest;
import com.hdekker.ai_workflow.domain.prompt.PromptResponse;

import reactor.core.publisher.Flux;

public interface LLMAdapter {
	
	Flux<PromptResponse> call(Flux<PromptRequest> request);

}

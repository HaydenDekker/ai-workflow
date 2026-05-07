package com.hdekker.ai_workflow.application.pipeline.port;

import com.hdekker.ai_workflow.domain.prompt.PromptRequest;
import com.hdekker.ai_workflow.domain.prompt.PromptResponse;

import reactor.core.publisher.Flux;

/**
 * Port interface for LLM service adapters.
 * <p>
 * Defines the contract that any LLM adapter (Map, Split, Reduction) must implement.
 * The application layer uses this port to depend on an abstraction, while concrete
 * implementations live in {@code adapter.outbound.llm}.
 */
public interface LLMAdapter {

	/**
	 * Calls the LLM with a stream of prompt requests and returns responses.
	 *
	 * @param request stream of prompt requests to send to the LLM
	 * @return stream of LLM responses
	 */
	Flux<PromptResponse> call(Flux<PromptRequest> request);

}

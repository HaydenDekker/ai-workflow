package com.hdekker.ai_workflow.pipeline;

import java.util.function.Function;

import org.springframework.messaging.Message;

import reactor.core.publisher.Flux;

public interface PromptMapped<T,K> {
	PromptPipelineBuilder<T,K> prompting(Function<Flux<T>, Flux<K>> prompt);
}

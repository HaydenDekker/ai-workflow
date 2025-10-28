package com.hdekker.ai_workflow.pipeline;

import java.util.function.Function;

import reactor.core.publisher.Flux;

public interface PromptMapped<T,K> {
	Persistable<T, K> prompting(Function<Flux<T>, Flux<K>> prompt);
}

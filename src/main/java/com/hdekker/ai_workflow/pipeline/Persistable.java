package com.hdekker.ai_workflow.pipeline;

import java.util.function.Consumer;

import reactor.core.publisher.Flux;

public interface Persistable<T, K> {
	PromptPipelineBuilder<T,K> persist(Consumer<K> object);
}

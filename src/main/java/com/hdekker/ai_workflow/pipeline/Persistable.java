package com.hdekker.ai_workflow.pipeline;

import java.util.function.Consumer;

public interface Persistable<T, K> {
	PromptPipelineBuilder<T,K> persist(Consumer<K> object);
}

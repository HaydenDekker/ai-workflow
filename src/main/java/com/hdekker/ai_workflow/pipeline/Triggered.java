package com.hdekker.ai_workflow.pipeline;

import reactor.core.publisher.Flux;

public interface Triggered<T, K> {
	PromptMapped<T, K> withTrigger(Flux<T> trigger);
}

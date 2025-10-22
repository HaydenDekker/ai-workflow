package com.hdekker.ai_workflow.pipeline;

import org.springframework.messaging.Message;

import reactor.core.publisher.Flux;

public interface Triggered {
	PromptMapped withTrigger(Flux<Message<String>> trigger);
}

package com.hdekker.ai_workflow.pipeline;

import java.util.function.Function;

import org.springframework.messaging.Message;

import reactor.core.publisher.Flux;

public interface PromptMapped {
	PromptPipelineBuilder prompting(Function<Flux<Message<String>>, Flux<String>> prompt);
}

package com.hdekker.ai_workflow.pipeline;

import java.util.function.Function;

import org.springframework.messaging.Message;

import reactor.core.publisher.Flux;

public class PromptPipelineBuilder<T, K> implements Triggered<T, K>, PromptMapped<T, K>{
	
	Flux<T> trigger;
	Function<Flux<T>, Flux<K>> prompt;
	
	public static <T,K> Triggered<T, K> instance() {
		return new PromptPipelineBuilder<T,K>();
	}

	@Override
	public PromptMapped<T,K> withTrigger(Flux<T> trigger) {
		this.trigger = trigger;
		return this;
	}

	@Override
	public PromptPipelineBuilder<T,K> prompting(Function<Flux<T>, Flux<K>> prompt) {
		this.prompt = prompt;
		return this;
	}
	
	public Flux<K> build(){
		return prompt.apply(trigger);
	}
	
	
	
	
	
	

}

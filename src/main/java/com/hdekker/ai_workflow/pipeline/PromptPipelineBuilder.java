package com.hdekker.ai_workflow.pipeline;

import java.util.function.Function;

import org.springframework.messaging.Message;

import reactor.core.publisher.Flux;

public class PromptPipelineBuilder implements Triggered, PromptMapped{
	
	Flux<Message<String>> trigger;
	Function<Flux<Message<String>>, Flux<String>> prompt;
	
	public static Triggered instance() {
		return new PromptPipelineBuilder();
	}

	@Override
	public PromptMapped withTrigger(Flux<Message<String>> trigger) {
		this.trigger = trigger;
		return this;
	}

	@Override
	public PromptPipelineBuilder prompting(Function<Flux<Message<String>>, Flux<String>> prompt) {
		this.prompt = prompt;
		return this;
	}
	
	public Flux<String> build(){
		return prompt.apply(trigger);
	}
	
	
	
	
	
	

}

package com.hdekker.ai_workflow.pipeline;

import java.util.function.Consumer;
import java.util.function.Function;

import com.hdekker.ai_workflow.llm.PromptResponseConverter;

import reactor.core.publisher.Flux;

public class PromptPipelineBuilder<T, K> implements

	Triggered<T, K>, 
	PromptMapped<T, K>,
	Persistable<T, K>,
	Splittable<T,K> {
	
	// check event will trigger 
	Flux<T> trigger;
	Function<Flux<T>, Flux<K>> prompt;
	Consumer<K> outputConsumer;
	PromptResponseConverter<K> splitter;
	
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
		return prompt.apply(trigger)
				.doOnNext(outputConsumer)
				.flatMap(p -> {
					return Flux.fromStream(splitter.convert(p).stream());
				});
	}

	@Override
	public Splittable<T,K> persist(Consumer<K> outputConsumer) {
		this.outputConsumer = outputConsumer;
		return this;
	}

	@Override
	public PromptPipelineBuilder<T, K> split(PromptResponseConverter<K> splitter) {
		this.splitter = splitter;
		return this;
	}

}

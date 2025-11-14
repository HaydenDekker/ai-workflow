package com.hdekker.ai_workflow.pipeline;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

import reactor.core.publisher.Flux;

public class PromptPipelineBuilder<T, K> implements

	Triggered<T, K>, 
	PromptMapped<T, K>,
	Persistable<T, K>,
	Splittable<T,K,K> {

	// check event will trigger 
	Flux<T> trigger;
	Function<Flux<T>, Flux<K>> prompt;
	Consumer<K> outputConsumer;
	SplittableStrategy<K, K> splitter;
	Optional<Enrichable<T>> enrichable = Optional.empty();
	
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
		return prompt.apply(
					enrichable.map(en -> trigger.map(input->en.enrich(input)))
						.orElse(trigger)
				)
				.doOnNext(outputConsumer)
				.flatMap(p -> {
					return Flux.fromStream(splitter.split(p).stream());
				});
	}

	@Override
	public Splittable<T,K,K> persist(Consumer<K> outputConsumer) {
		this.outputConsumer = outputConsumer;
		return this;
	}

	@Override
	public PromptPipelineBuilder<T, K> split(SplittableStrategy<K, K> splitter) {
		this.splitter = splitter;
		return this;
	}

	@Override
	public PromptMapped<T,K> enrichFirst(Enrichable<T> enrich) {
		this.enrichable = Optional.of(enrich);
		return this;
	}

}

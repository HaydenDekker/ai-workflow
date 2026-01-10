package com.hdekker.ai_workflow.app.pipeline;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

import com.hdekker.ai_workflow.pipeline.SplittableStrategy;

import reactor.core.publisher.Flux;

public class PromptPipelineBuilder<T, K> {

	public static interface Triggered<T,K> {
		PromptMapped<T,K> withTrigger(Flux<T> trigger);
	}
	
	public interface PromptMapped<T,K> {
		PromptMapped<T, K> enrichFirst(Enrichable<T> enrich);
		Persistable<K> prompting(Function<Flux<T>, Flux<K>> prompt);
	}
	
	public interface Enrichable<T> {
		T enrich(T input);
	}
	
	public interface Persistable<K> {
		Splittable<K> persist(Consumer<K> object);
	}

	public interface Splittable<K> {
		BuilderImpl<?, K> split(SplittableStrategy<K,K> splitter);
	}
	
	public static class BuilderImpl<T,K> implements
	Triggered<T, K>, 
	PromptMapped<T,K>,
	Persistable<K>,
	Splittable<K> {
		
		Flux<T> trigger;
		Function<Flux<T>, Flux<K>> prompt;
		Consumer<K> outputConsumer;
		SplittableStrategy<K, K> splitter;
		Optional<Enrichable<T>> enrichable = Optional.empty();
		
		@Override
		public PromptMapped<T,K> withTrigger(Flux<T> trigger) {
			this.trigger = trigger;
			return this;
		}
		
		@Override
		public PromptMapped<T,K> enrichFirst(Enrichable<T> enrich) {
			this.enrichable = Optional.of(enrich);
			return this;
		}
		
		@Override
		public Persistable<K> prompting(Function<Flux<T>, Flux<K>> prompt) {
			this.prompt = prompt;
			return this;
		}
		
		
		@Override
		public Splittable<K> persist(Consumer<K> outputConsumer) {
			this.outputConsumer = outputConsumer;
			return this;
		}

		@Override
		public BuilderImpl<T, K> split(SplittableStrategy<K, K> splitter) {
			this.splitter = splitter;
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

	}


	
	public static <T,K> Triggered<T, K> instance() {
		return new BuilderImpl<T,K>();
	}


}

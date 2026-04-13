package com.hdekker.ai_workflow.app.pipeline;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hdekker.ai_workflow.pipeline.SplittableStrategy;
import com.hdekker.ai_workflow.pipeline.domain.AgentDefinition;
import com.hdekker.ai_workflow.prompt.PromptRequest;
import com.hdekker.ai_workflow.prompt.PromptResponse;

import reactor.core.publisher.Flux;

public class AgentBuilder {
	
	public static interface WithAgentDefinition {
		Triggered withDefinition(AgentDefinition agentDefinition);
	}

	public static interface Triggered {
		PromptMapped withTrigger(Flux<PromptRequest> trigger);
	}
	
	public interface PromptMapped {
		PromptMapped enrichFirst(Enrichable enrich);
		Persistable prompting(Function<Flux<PromptRequest>, Flux<PromptResponse>> prompt);
	}
	
	public interface Enrichable {
		PromptRequest enrich(PromptRequest input);
	}
	
	public interface Persistable {
		Splittable persist(Consumer<PromptResponse> object);
	}

	public interface Splittable {
		BuilderImpl split(SplittableStrategy splitter);
	}
	
	public static class BuilderImpl implements
	WithAgentDefinition,
	Triggered, 
	PromptMapped,
	Persistable,
	Splittable {
		
		Logger log = LoggerFactory.getLogger(BuilderImpl.class);
		
		Flux<PromptRequest> trigger;
		Function<Flux<PromptRequest>, Flux<PromptResponse>> prompt;
		Consumer<PromptResponse> outputConsumer;
		SplittableStrategy splitter;
		Optional<Enrichable> enrichable = Optional.empty();
		AgentDefinition agentDefinition;
		
		@Override
		public Triggered withDefinition(AgentDefinition agentDefinition) {
			this.agentDefinition = agentDefinition;
			return this;
		}
		
		@Override
		public PromptMapped withTrigger(Flux<PromptRequest> trigger) {
			this.trigger = trigger
					.filter(pr-> agentDefinition.inputRegexMatches(pr.fileURL()))
					.doOnNext(pr-> log.info("Agent: " + agentDefinition.title() + " accepted file " + pr.fileURL()));
			return this;
		}
		
		@Override
		public PromptMapped enrichFirst(Enrichable enrich) {
			this.enrichable = Optional.of(enrich);
			return this;
		}
		
		@Override
		public Persistable prompting(Function<Flux<PromptRequest>, Flux<PromptResponse>> prompt) {
			this.prompt = prompt;
			return this;
		}
		
		
		@Override
		public Splittable persist(Consumer<PromptResponse> outputConsumer) {
			this.outputConsumer = outputConsumer;
			return this;
		}

		@Override
		public BuilderImpl split(SplittableStrategy splitter) {
			this.splitter = splitter;
			return this;
		}
		
	
		public Flux<PromptResponse> build(){
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



	public static WithAgentDefinition instance() {
		return new BuilderImpl();
	}


}

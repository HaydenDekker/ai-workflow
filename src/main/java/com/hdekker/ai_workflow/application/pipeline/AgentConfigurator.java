package com.hdekker.ai_workflow.application.pipeline;

import java.util.function.Consumer;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hdekker.ai_workflow.adapter.outbound.llm.LLMAdapterFactory;
import com.hdekker.ai_workflow.application.pipeline.port.LLMAdapter;
import com.hdekker.ai_workflow.application.agent.port.FileWritePort;
import com.hdekker.ai_workflow.domain.agent.AgentDefinition;
import com.hdekker.ai_workflow.domain.file.FileHistory;
import com.hdekker.ai_workflow.domain.prompt.PromptResponse;

import org.springframework.ai.chat.client.ChatClient;
import reactor.core.publisher.Flux;

/**
 * Configures the agent processing pipeline by wiring together:
 * file input → LLM processing → output persistence → splitting.
 * <p>
 * Application-layer orchestrator — depends on port interfaces
 * ({@link FileWritePort}) rather than concrete infrastructure.
 */
public class AgentConfigurator {

	private static final Logger log = LoggerFactory.getLogger(AgentConfigurator.class);

	private final Flux<FileHistory> fileInputFlux;
	private final ChatClient chatClient;
	private final Consumer<PromptResponse> persister;
	private final FileWritePort fileWritePort;
	private final AgentObserverUseCase observer;

	/**
	 * Creates an AgentConfigurator with a direct persister consumer.
	 */
	public AgentConfigurator(
			Flux<FileHistory> fileInputFlux,
			ChatClient chatClient,
			Consumer<PromptResponse> persister) {
		this(fileInputFlux, chatClient, persister, null, null);
	}

	/**
	 * Creates an AgentConfigurator that uses a FileWritePort.
	 * If port is null, falls back to direct persister (backward compatible).
	 */
	public AgentConfigurator(
			Flux<FileHistory> fileInputFlux,
			ChatClient chatClient,
			Consumer<PromptResponse> persister,
			FileWritePort fileWritePort) {
		this(fileInputFlux, chatClient, persister, fileWritePort, null);
	}

	/**
	 * Creates an AgentConfigurator with an agent observer for pipeline-level
	 * dispatch and storage tracking.
	 *
	 * @param fileInputFlux the file input flux from the scanner
	 * @param chatClient    the LLM chat client
	 * @param persister     the response persister consumer (fallback when
	 *                      fileWritePort is null)
	 * @param fileWritePort the file write port (nullable, used when available)
	 * @param observer      the agent observer use case (nullable for backward
	 *                      compatibility)
	 */
	public AgentConfigurator(
			Flux<FileHistory> fileInputFlux,
			ChatClient chatClient,
			Consumer<PromptResponse> persister,
			FileWritePort fileWritePort,
			AgentObserverUseCase observer) {
		this.fileInputFlux = fileInputFlux;
		this.chatClient = chatClient;
		this.persister = persister;
		this.fileWritePort = fileWritePort;
		this.observer = observer;
	}

	/**
	 * Configures and builds the processing pipeline for the given agent.
	 *
	 * @param agentDefinition the agent's configuration
	 * @return the configured Flux of prompt responses
	 */
	public Flux<PromptResponse> configure(AgentDefinition agentDefinition) {

		LLMAdapter adapter = LLMAdapterFactory.create(chatClient, agentDefinition);

		// Use FileWritePort if available, otherwise use direct persister
		Consumer<PromptResponse> effectivePersister = fileWritePort != null
				? fileWritePort.createPersister(null)
				: persister;

		// Build pipeline without real persister — we add dispatch/storage hooks
		// separately below. No-op consumer satisfies the Persistable interface.
		Flux<PromptResponse> basePipeline = AgentBuilder.instance()
				.withDefinition(agentDefinition)
				.withTrigger(fileInputFlux
						.map(fh -> fh.to()))
				.prompting(adapter::call)
				.persist(response -> { })
				.split(SplittableStrategy.noSPLT())
				.build();

		// Dispatch hook fires first — after LLM returns, before persist
		Flux<PromptResponse> withDispatch = basePipeline.doOnNext(response -> {
			if (observer != null) {
				observer.recordDispatch(agentDefinition.title(), response.fileName());
				log.info("Dispatch recorded: agent={}, file={}", agentDefinition.title(), response.fileName());
			} else {
				log.warn("AgentObserverUseCase is null — dispatch NOT recorded for agent {}", agentDefinition.title());
			}
		});

		// Storage hook fires second — after dispatch, during persist
		return withDispatch.doOnNext(response -> {
			effectivePersister.accept(response);
			if (observer != null) {
				observer.recordStorage(agentDefinition.title(), response.fileName(), null);
				log.info("Storage recorded: agent={}, file={}", agentDefinition.title(), response.fileName());
			}
		});
	}
}

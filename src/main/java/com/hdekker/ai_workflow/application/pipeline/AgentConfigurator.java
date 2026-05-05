package com.hdekker.ai_workflow.application.pipeline;

import java.util.function.Consumer;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hdekker.ai_workflow.adapter.outbound.llm.LLMAdapter;
import com.hdekker.ai_workflow.adapter.outbound.llm.LLMAdapterFactory;
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

	/**
	 * Creates an AgentConfigurator with a direct persister consumer.
	 */
	public AgentConfigurator(
			Flux<FileHistory> fileInputFlux,
			ChatClient chatClient,
			Consumer<PromptResponse> persister) {
		this(fileInputFlux, chatClient, persister, null);
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
		this.fileInputFlux = fileInputFlux;
		this.chatClient = chatClient;
		this.persister = persister;
		this.fileWritePort = fileWritePort;
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

		return AgentBuilder.instance()
				.withDefinition(agentDefinition)
				.withTrigger(fileInputFlux
						.map(fh -> fh.to()))
				.prompting(adapter::call)
				.persist(effectivePersister)
				.split(SplittableStrategy.noSPLT())
				.build();
	}
}

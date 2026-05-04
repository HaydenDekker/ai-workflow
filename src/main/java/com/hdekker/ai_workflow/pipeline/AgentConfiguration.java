package com.hdekker.ai_workflow.pipeline;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.hdekker.ai_workflow.adapter.outbound.llm.LLMOutputParsingUtils;
import com.hdekker.ai_workflow.domain.agent.AgentDefinition;
import com.hdekker.ai_workflow.domain.prompt.PromptResponse;
import com.hdekker.ai_workflow.adapter.outbound.file.FileSystemScannerConfig;
import com.hdekker.ai_workflow.prompt.PromptConfiguration;
import com.hdekker.ai_workflow.prompt.SystemPromptConfiguration;
import com.hdekker.ai_workflow.usecases.AgentLifecycleUseCase;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

/**
 *  To build the configured agents ready for file processing.
 *  <p>
 *  Conditionally loaded via {@code yaml.agents.enabled=true} (default: true).
 *  Set to {@code false} in tests that need to load {@code SystemPromptConfiguration}
 *  in isolation without triggering agent persistence to the database.
 *
 */
@Configuration
@ConditionalOnProperty(name = "yaml.agents.enabled", havingValue = "true", matchIfMissing = true)
public class AgentConfiguration {
	
	Logger log = LoggerFactory.getLogger(AgentConfiguration.class);
	
	@Autowired
	FileSystemScannerConfig fileScannerConfig;
	
	@Autowired
	PromptConfiguration promptConfiguration;
	
	ObjectMapper om = new ObjectMapper();
	
	SplittableStrategy jsonItemListConverter = (s)->{
		String json = LLMOutputParsingUtils.extractJsonContent(s.response());
		List<Object> list = List.of();
		try {
			list = om.readValue(json, new TypeReference<List<Object>>() {});
		} catch (JsonProcessingException e) {
			log.error("Unexpected LLM response " + s.response());
			e.printStackTrace();
		}
		return list
			.stream()
			.map(resp->new PromptResponse(s.prompt(), s.fileName(), s.fileContents(), resp.toString()))
			.toList();
	};
	
	@Autowired
	SystemPromptConfiguration systemPromptConfiguration;

	ChatClient chatClient;

	// TODO component and pass in.
	Path outputFolderPath;

	@Autowired
	AgentLifecycleUseCase dynamicAgentManager;

	public AgentConfiguration(
			PromptConfiguration promptConfiguration,
			SystemPromptConfiguration systemPromptConfiguration,
			FileSystemScannerConfig fileScannerConfig,
			ChatClient chatClient,
			AgentLifecycleUseCase dynamicAgentManager) {

		this.promptConfiguration = promptConfiguration;
		this.systemPromptConfiguration = systemPromptConfiguration;
		this.fileScannerConfig = fileScannerConfig;
		this.chatClient = chatClient;

		try {
			outputFolderPath = fileScannerConfig.getUrl().getFile().toPath();
		} catch (IOException e) {
			e.printStackTrace();
		}

// Initialize YAML agents through manager
		List<AgentDefinition> yamlAgents = systemPromptConfiguration.getAgentWorkflows()
			.stream()
			.peek(wf-> log.info("Configuring agent workflow: " + wf.agents().get(0).title()))
			.flatMap(wf-> wf.agents().stream())
			.toList();
		
		log.info("" + yamlAgents.size() + " agents pre-configured.");

		dynamicAgentManager.initializeFromYAML(yamlAgents);
	}
}

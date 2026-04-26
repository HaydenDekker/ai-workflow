package com.hdekker.ai_workflow.pipeline;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hdekker.ai_workflow.usecases.AgentLifecycleUseCase;
import com.hdekker.ai_workflow.pipeline.domain.AgentDefinition;
import com.hdekker.ai_workflow.files.FileSystemRecursiveFileScannerAdapter;
import com.hdekker.ai_workflow.files.FileSystemScannerConfig;
import com.hdekker.ai_workflow.llm.output.LLMOutputParsingUtils;
import com.hdekker.ai_workflow.prompt.PromptConfiguration;
import com.hdekker.ai_workflow.prompt.PromptResponse;
import com.hdekker.ai_workflow.prompt.SystemPromptConfiguration;

/**
 *  To build the configured agents ready for file processing.
 * 
 */
@Configuration
public class AgentConfiguration {
	
	Logger log = LoggerFactory.getLogger(AgentConfiguration.class);
	
	@Autowired
	FileSystemScannerConfig fileScannerConfig;

	@Autowired
	FileSystemRecursiveFileScannerAdapter fileScanner;
	
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
			FileSystemRecursiveFileScannerAdapter fileScanner,
			PromptConfiguration promptConfiguration,
			SystemPromptConfiguration systemPromptConfiguration,
			FileSystemScannerConfig fileScannerConfig,
			ChatClient chatClient,
			AgentLifecycleUseCase dynamicAgentManager) {

		this.fileScanner = fileScanner;
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

package com.hdekker.ai_workflow.pipeline.management;

import java.io.IOException;
import java.nio.file.Path;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.hdekker.ai_workflow.app.pipeline.management.DynamicAgentManager;
import com.hdekker.ai_workflow.database.agent.AgentPersistenceService;
import com.hdekker.ai_workflow.files.FileSystemFileWriter;
import com.hdekker.ai_workflow.files.FileSystemRecursiveFileScannerAdapter;
import com.hdekker.ai_workflow.files.FileSystemScannerConfig;

@Configuration
public class DynamicAgentManagerConfiguration {

	@Bean
	public DynamicAgentManager dynamicAgentManager(
			FileSystemRecursiveFileScannerAdapter fileScanner,
			FileSystemScannerConfig fileScannerConfig,
			ChatClient chatClient,
			FileSystemFileWriter fileWriter,
			AgentPersistenceService agentPersistenceService) throws IOException {
		Path outputFolderPath = fileScannerConfig.getUrl().getFile().toPath();
		return new DynamicAgentManager(fileScanner, fileWriter, outputFolderPath, chatClient, agentPersistenceService);
	}

}

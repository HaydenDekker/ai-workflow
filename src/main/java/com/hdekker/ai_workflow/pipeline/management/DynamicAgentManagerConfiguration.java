package com.hdekker.ai_workflow.pipeline.management;

import java.io.IOException;
import java.nio.file.Path;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.hdekker.ai_workflow.app.pipeline.management.DynamicAgentManager;
import com.hdekker.ai_workflow.app.pipeline.management.ScannerRegistry;
import com.hdekker.ai_workflow.database.agent.AgentPersistenceService;
import com.hdekker.ai_workflow.files.FileSystemFileWriter;
import com.hdekker.ai_workflow.files.FileSystemScannerConfig;

/**
 * Configuration for DynamicAgentManager.
 * <p>
 * In Phase 2, the manager now accepts a ScannerRegistry instead of a single FileScanner.
 * Each dynamic agent gets its own scanner instance managed by the registry.
 * <p>
 * The old FileSystemRecursiveFileScannerAdapter is retained as a bean for backward
 * compatibility with YAML agents that don't specify a target directory.
 */
@Configuration
public class DynamicAgentManagerConfiguration {

	@Bean
	public DynamicAgentManager dynamicAgentManager(
			ScannerRegistry scannerRegistry,
			FileSystemScannerConfig fileScannerConfig,
			ChatClient chatClient,
			FileSystemFileWriter fileWriter,
			AgentPersistenceService agentPersistenceService) throws IOException {
		Path outputFolderPath = fileScannerConfig.getUrl().getFile().toPath();
		return new DynamicAgentManager(
				scannerRegistry,
				fileWriter,
				outputFolderPath,
				chatClient,
				agentPersistenceService);
	}
}

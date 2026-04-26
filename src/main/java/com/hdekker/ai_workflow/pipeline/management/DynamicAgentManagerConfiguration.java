package com.hdekker.ai_workflow.pipeline.management;

import java.io.IOException;
import java.nio.file.Path;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.hdekker.ai_workflow.usecases.AgentLifecycleUseCase;
import com.hdekker.ai_workflow.app.pipeline.management.ScannerRegistry;
import com.hdekker.ai_workflow.database.agent.AgentPersistenceUsecase;
import com.hdekker.ai_workflow.files.FileSystemFileWriter;
import com.hdekker.ai_workflow.files.FileSystemScannerConfig;

/**
 * Configuration for AgentLifecycleUseCase.
 * <p>
 * In Phase 2, the use case now accepts a ScannerRegistry instead of a single FileScanner.
 * Each dynamic agent gets its own scanner instance managed by the registry.
 * <p>
 * The old FileSystemRecursiveFileScannerAdapter is retained as a bean for backward
 * compatibility with YAML agents that don't specify a target directory.
 */
@Configuration
public class DynamicAgentManagerConfiguration {

	@Bean
	public AgentLifecycleUseCase dynamicAgentManager(
			ScannerRegistry scannerRegistry,
			FileSystemScannerConfig fileScannerConfig,
			ChatClient chatClient,
			FileSystemFileWriter fileWriter,
			AgentPersistenceUsecase agentPersistenceUsecase) throws IOException {
		Path outputFolderPath = fileScannerConfig.getUrl().getFile().toPath();
		return new AgentLifecycleUseCase(
				scannerRegistry,
				fileWriter,
				outputFolderPath,
				chatClient,
				agentPersistenceUsecase);
	}
}

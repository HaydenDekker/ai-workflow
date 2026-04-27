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
 * The use case accepts a ScannerRegistry so each dynamic agent
 * gets its own scanner instance managed by the registry.
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

package com.hdekker.ai_workflow.pipeline.management;

import java.io.IOException;
import java.nio.file.Path;

import com.hdekker.ai_workflow.adapter.outbound.persistence.agent.AgentRepositoryAdapter;
import com.hdekker.ai_workflow.app.pipeline.management.ScannerRegistry;
import com.hdekker.ai_workflow.adapter.outbound.file.FileSystemFileWriter;
import com.hdekker.ai_workflow.adapter.outbound.file.FileSystemScannerConfig;
import com.hdekker.ai_workflow.adapter.outbound.file.TargetDirectoryValidator;
import com.hdekker.ai_workflow.usecases.AgentLifecycleUseCase;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for AgentLifecycleUseCase.
 * <p>
 * The use case accepts a ScannerRegistry so each dynamic agent
 * gets its own scanner instance managed by the registry.
 */
@Configuration
public class DynamicAgentManagerConfiguration {

	@Bean
	public TargetDirectoryValidator targetDirectoryValidator() {
		return new TargetDirectoryValidator();
	}

	@Bean
	public AgentLifecycleUseCase dynamicAgentManager(
			ScannerRegistry scannerRegistry,
			FileSystemScannerConfig fileScannerConfig,
			ChatClient chatClient,
			FileSystemFileWriter fileWriter,
			AgentRepositoryAdapter agentPersistenceUsecase,
			TargetDirectoryValidator targetDirectoryValidator) throws IOException {
		Path outputFolderPath = fileScannerConfig.getUrl().getFile().toPath();
		return new AgentLifecycleUseCase(
				scannerRegistry,
				fileWriter,
				outputFolderPath,
				chatClient,
				agentPersistenceUsecase,
				targetDirectoryValidator);
	}
}

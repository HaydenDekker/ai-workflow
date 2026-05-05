package com.hdekker.ai_workflow.config;

import java.io.IOException;
import java.nio.file.Path;

import com.hdekker.ai_workflow.adapter.outbound.file.FileSystemScannerConfig;
import com.hdekker.ai_workflow.adapter.outbound.file.TargetDirectoryValidator;
import com.hdekker.ai_workflow.application.agent.AgentLifecycleService;
import com.hdekker.ai_workflow.application.agent.port.AgentRepository;
import com.hdekker.ai_workflow.application.agent.port.DirectoryValidationPort;
import com.hdekker.ai_workflow.application.agent.port.FileWritePort;
import com.hdekker.ai_workflow.application.pipeline.ScannerRegistry;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for {@link AgentLifecycleService}.
 * <p>
 * The service accepts a {@link ScannerRegistry} so each dynamic agent
 * gets its own scanner instance managed by the registry.
 * Depends on port interfaces rather than concrete infrastructure.
 */
@Configuration
public class DynamicAgentManagerConfiguration {

	@Bean
	public TargetDirectoryValidator targetDirectoryValidator() {
		return new TargetDirectoryValidator();
	}

	@Bean
	public AgentLifecycleService agentLifecycleService(
			ScannerRegistry scannerRegistry,
			FileWritePort fileWritePort,
			FileSystemScannerConfig fileScannerConfig,
			ChatClient chatClient,
			AgentRepository agentRepository,
			DirectoryValidationPort directoryValidationPort) throws IOException {
		Path outputFolderPath = fileScannerConfig.getUrl().getFile().toPath();
		return new AgentLifecycleService(
				scannerRegistry,
				fileWritePort,
				outputFolderPath,
				chatClient,
				agentRepository,
				directoryValidationPort);
	}
}

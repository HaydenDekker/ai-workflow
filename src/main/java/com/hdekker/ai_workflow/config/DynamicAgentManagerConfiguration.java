package com.hdekker.ai_workflow.config;

import java.time.Duration;

import com.hdekker.ai_workflow.adapter.outbound.file.FileSystemFileCounter;
import com.hdekker.ai_workflow.adapter.outbound.file.TargetDirectoryValidator;
import com.hdekker.ai_workflow.application.file.port.FileCounterPort;
import com.hdekker.ai_workflow.application.file.port.FileMetadataRepository;
import com.hdekker.ai_workflow.application.file.port.FileWatcherPort;
import com.hdekker.ai_workflow.application.pipeline.ScannerRegistry;
import com.hdekker.ai_workflow.application.scanner.ScannerObservabilityUseCase;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

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
	@Primary
	public FileCounterPort fileCounterPort() {
		return new FileSystemFileCounter();
	}

	@Bean
	public ScannerRegistry scannerRegistry(
			FileMetadataRepository fileMetadataRepository,
			ScannerObservabilityUseCase observability,
			FileWatcherPort fileWatcherFactory,
			FileCounterPort fileCounterPort) {
		return new ScannerRegistry(
				fileMetadataRepository,
				observability,
				fileWatcherFactory,
				fileCounterPort,
				Duration.ofSeconds(2),
				Duration.ofSeconds(1));
	}
}

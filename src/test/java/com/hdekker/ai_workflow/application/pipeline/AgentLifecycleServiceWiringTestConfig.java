package com.hdekker.ai_workflow.application.pipeline;

import java.nio.file.Path;
import java.util.function.Consumer;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Primary;

import com.hdekker.ai_workflow.application.agent.AgentLifecycleService;
import com.hdekker.ai_workflow.application.agent.port.AgentRepository;
import com.hdekker.ai_workflow.application.agent.port.DirectoryValidationPort;
import com.hdekker.ai_workflow.application.agent.port.FileWritePort;
import com.hdekker.ai_workflow.application.file.port.FileCounterPort;
import com.hdekker.ai_workflow.application.pipeline.port.AgentObserverEventPort;
import com.hdekker.ai_workflow.application.pipeline.port.AgentObserverPort;
import com.hdekker.ai_workflow.domain.prompt.PromptResponse;
import com.hdekker.ai_workflow.test.harness.mock.ChatClientMockBuilder;

import org.springframework.ai.chat.client.ChatClient;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Minimal Spring configuration for testing agent observer bean wiring.
 * <p>
 * Disables component scanning to prevent @Service classes from being auto-detected.
 * Only the beans explicitly defined here are created, avoiding conflicts with
 * the full application context (which requires external services like OpenAI).
 */
@Configuration
@ComponentScan(useDefaultFilters = false)
public class AgentLifecycleServiceWiringTestConfig {

    private static final Path TEST_OUTPUT_DIR = Path.of("/tmp/test-output");

    @Bean
    @Primary
    public FileCounterPort fileCounterPort() {
        return new com.hdekker.ai_workflow.adapter.outbound.file.FileSystemFileCounter();
    }

    @Bean
    @Primary
    public AgentObserverEventPort agentObserverEventPort() {
        return new AgentObserverEventBus();
    }

    @Bean
    @Primary
    public AgentObserverPort agentObserverPort(
            FileCounterPort fileCounterPort,
            @Value("${ai.workflow.output.directory:/tmp/test-output}") String outputDirectory) {
        return new AgentObserverService(fileCounterPort, outputDirectory);
    }

    @Bean
    @Primary
    public AgentObserverUseCase agentObserverUseCase(
            AgentObserverPort agentObserverPort,
            AgentObserverEventPort agentObserverEventPort) {
        return new AgentObserverUseCase(agentObserverPort, agentObserverEventPort);
    }

    @Bean
    @Primary
    public AgentLifecycleService agentLifecycleService(
            AgentObserverUseCase observer) {
        ChatClient chatClient = ChatClientMockBuilder.createMock("mock response");

        FileWritePort fileWritePort = mock(FileWritePort.class);
        when(fileWritePort.createPersister(any())).thenAnswer(inv -> (Consumer<PromptResponse>) pr -> {});

        AgentRepository agentRepo = mock(AgentRepository.class);
        DirectoryValidationPort validator = mock(DirectoryValidationPort.class);
        when(validator.validate(any())).thenReturn(DirectoryValidationPort.ValidationResult.success());

        ScannerRegistry scannerRegistry = mock(ScannerRegistry.class);

        return new AgentLifecycleService(
                scannerRegistry,
                fileWritePort,
                TEST_OUTPUT_DIR,
                chatClient,
                agentRepo,
                validator,
                observer);
    }
}

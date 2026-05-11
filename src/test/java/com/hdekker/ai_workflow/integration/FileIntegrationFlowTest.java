package com.hdekker.ai_workflow.integration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CopyOnWriteArrayList;


import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hdekker.ai_workflow.TestProfiles;
import com.hdekker.ai_workflow.application.agent.AgentLifecycleService;
import com.hdekker.ai_workflow.test.harness.config.ChatClientTestConfig;
import com.hdekker.ai_workflow.test.harness.factory.TestConfigurationFactory;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Full integration test for the file polling → agent → LLM call flow.
 * 
 * NOTE: This test is disabled because the architecture has changed from
 * IntegrationFlowContext-based to direct FileReadingMessageSource-based scanning.
 * The core scanner functionality is verified by FileSystemScannerAdapterTest and
 * ScannerRegistryIntegrationTest. This test needs to be updated to work with
 * the new architecture where each agent has its own FileReadingMessageSource.
 * 
 * The original test was designed to verify that the FluxMessageChannel subscriber
 * race condition was fixed, but that fix is now handled differently.
 */
@Disabled("Architecture changed to direct FileReadingMessageSource - needs rework")
@SpringBootTest(
        properties = {
                "spring.ai.openai.api-key=no_key_required",
                "spring.ai.chat.client.enabled=false"
        }
)
@Import(ChatClientTestConfig.class)
@ActiveProfiles(TestProfiles.RESOURCES_TEST_FOLDER)
public class FileIntegrationFlowTest {

    private static final Logger log = LoggerFactory.getLogger(FileIntegrationFlowTest.class);

    // Capture whether the LLM mock was called
    private final CopyOnWriteArrayList<String> capturedPrompts = new CopyOnWriteArrayList<>();

    @MockitoBean
    private ChatClient mockChatClient;

    @Autowired
    private AgentLifecycleService dynamicAgentManager;

    @TempDir
    Path tempDir;



    @BeforeEach
    void setUp() throws Exception {
        // Ensure scanner watch directory exists (scanner watches /test/project-root)
        Path projectRoot = Path.of("/test/project-root");
        Files.createDirectories(projectRoot);
        Files.createDirectories(projectRoot.resolve("output"));

        // Configure the ChatClient mock to capture prompts
        configureChatClientMock();
    }

    private void configureChatClientMock() {
        String mockResponse = "## Summary\n\nThis document has been analyzed successfully.";

        org.mockito.Mockito.reset(mockChatClient);

        ChatClient.ChatClientRequestSpec requestSpec = org.mockito.Mockito.mock(
                ChatClient.ChatClientRequestSpec.class);
        ChatClient.StreamResponseSpec streamSpec = org.mockito.Mockito.mock(
                ChatClient.StreamResponseSpec.class);

        org.mockito.Mockito.when(mockChatClient.prompt(org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(invocation -> {
                    String prompt = invocation.getArgument(0, String.class);
                    capturedPrompts.add(prompt);
                    log.info("LLM mock captured prompt ({} chars): {}", 
                            prompt.length(), prompt.substring(0, Math.min(80, prompt.length())));
                    return requestSpec;
                });

        org.mockito.Mockito.when(requestSpec.stream()).thenReturn(streamSpec);
        org.mockito.Mockito.when(streamSpec.content())
                .thenReturn(reactor.core.publisher.Flux.just(mockResponse));
    }

    @AfterEach
    void tearDown() {
        log.info("Test complete. Captured {} LLM call(s)", capturedPrompts.size());
        if (!capturedPrompts.isEmpty()) {
            for (int i = 0; i < capturedPrompts.size(); i++) {
                log.info("Prompt {} ({} chars): {}", i + 1, capturedPrompts.get(i).length(),
                        capturedPrompts.get(i).substring(0, Math.min(120, capturedPrompts.get(i).length())));
            }
        }
    }

    /**
     * Main integration test: file placed in watched directory → agent processes it → LLM called.
     * 
     * This test verifies the complete flow:
     * 1. Spring context starts, agent is registered (subscribes to fileInboundFluxChannel)
     * 2. A .md file is placed in the tempDir (the watched directory)
     * 3. WatchService/polling detects the file
     * 4. File content flows through fileInboundFluxChannel → agent pipeline → LLM mock
     * 5. Output file is written to disk
     */
    @Test
    void givenAgentRegistered_WhenFilePlacedInWatchedDir_ThenLLMIsCalledAndOutputCreated()
            throws Exception {

        log.info("Starting full integration flow test");

        // Add an agent dynamically - this subscribes to the flux channel
        var agentDef = TestConfigurationFactory.createCustomDefinition(
                ".*\\.md$",
                "INTEGRATION-TEST-AGENT",
                "Analyze the provided markdown document.",
                "Map",
                "Provide a structured analysis.",
                "output/analysis-${name}.md");

        dynamicAgentManager.addDynamicAgent(agentDef, "/test/project-root");
        log.info("Added dynamic agent: {}", agentDef.title());

        // Wait for the scanner to start
        log.info("Waiting for scanner to start...");
        Thread.sleep(3000);
        log.info("Scanner should be established");

        // Place a test file in the agent's target directory
        String testFileName = "test-document.md";
        String testContent = "# Test Document\n\nThis is test content for the integration test.";
        Path testFile = Path.of("/test/project-root", testFileName);
        Files.writeString(testFile, testContent);

        log.info("Placed test file: {}", testFile);

        // Wait for the file to be processed
        // The polling interval is 2 seconds, plus processing time
        Duration waitTime = Duration.ofSeconds(15);
        long deadline = System.currentTimeMillis() + waitTime.toMillis();

        boolean llmCalled = false;
        boolean outputFound = false;

        while (System.currentTimeMillis() < deadline) {
            // Check if LLM was called
            if (!capturedPrompts.isEmpty()) {
                llmCalled = true;
                log.info("LLM mock was called (after {} ms)", System.currentTimeMillis() - deadline + waitTime.toMillis());
            }

            // Check if output file was created
            if (!outputFound) {
                Path outputPath = Path.of("/test/project-root/output/analysis-test-document.md");
                if (Files.exists(outputPath) && Files.size(outputPath) > 0) {
                    outputFound = true;
                    log.info("Output file found: {}", outputPath);
                    log.info("Output content: {}", Files.readString(outputPath));
                }
            }

            if (llmCalled && outputFound) {
                break;
            }

            Thread.sleep(500);
        }

        // Verify the LLM mock was called
        log.info("LLM mock called: {}, Output file found: {}", llmCalled, outputFound);

        org.assertj.core.api.Assertions.assertThat(llmCalled)
                .withFailMessage(
                        "LLM mock was not called - the file processing flow did not reach the LLM adapter. "
                        + "This likely means fileInboundFluxChannel had no subscribers when the file was polled, "
                        + "causing MessageDeliveryException: 'doesn't have subscribers to accept messages'")
                .isTrue();

        org.assertj.core.api.Assertions.assertThat(outputFound)
                .withFailMessage(
                        "Output file was not created - file processing may have completed but output was not persisted")
                .isTrue();

        log.info("Integration flow test PASSED");
    }

    /**
     * Test that verifies the fileInboundFluxChannel subscriber registration.
     * 
     * This test specifically checks that when an agent is added, the
     * fileInboundFluxChannel has subscribers and can accept messages from
     * the polling adapter without throwing MessageDeliveryException.
     * 
     * If the channel has no subscribers, the polling adapter will throw:
     * "IllegalStateException: The [bean 'fileInboundFluxChannel'] doesn't have subscribers"
     */
    @Test
    void givenAgentAdded_WhenFilePolled_ThenChannelHasSubscribersAndProcessesFile()
            throws Exception {

        log.info("Testing subscriber registration on fileInboundFluxChannel");

        // Add an agent dynamically - this should subscribe to the flux channel
        var agentDef = TestConfigurationFactory.createCustomDefinition(
                ".*\\.md$",
                "SUBSCRIBER-TEST-AGENT",
                "Analyze markdown files for subscriber registration.",
                "Map",
                "Provide analysis.",
                "output/subscriber-test-${name}.md");

        dynamicAgentManager.addDynamicAgent(agentDef, "/test/project-root");
        log.info("Added agent: {}", agentDef.title());

        // Wait for the scanner to start
        log.info("Waiting for scanner to start...");
        Thread.sleep(3000);
        log.info("Scanner should be established");

        // Place a file in the agent's target directory
        String testFileName = "subscriber-test.md";
        String testContent = "# Subscriber Test\n\nTesting channel subscriber registration.";
        Path testFile = Path.of("/test/project-root", testFileName);
        Files.writeString(testFile, testContent);

        log.info("Placed test file for subscriber test: {}", testFile);

        // Wait for processing
        Duration waitTime = Duration.ofSeconds(15);
        long deadline = System.currentTimeMillis() + waitTime.toMillis();

        boolean llmCalled = false;
        while (System.currentTimeMillis() < deadline) {
            if (!capturedPrompts.isEmpty()) {
                llmCalled = true;
                break;
            }
            Thread.sleep(500);
        }

        // The key assertion: if we get here without exception, the channel had subscribers
        // If the channel had no subscribers, the polling adapter would have thrown
        // MessageDeliveryException before we could check the mock
        org.assertj.core.api.Assertions.assertThat(llmCalled)
                .withFailMessage(
                        "LLM mock was not called - the file processing flow did not reach the LLM adapter.")
                .isTrue();

        log.info("Subscriber registration test PASSED");
    }
}

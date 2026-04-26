package com.hdekker.ai_workflow.pipeline;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.hdekker.ai_workflow.TestProfiles;
import com.hdekker.ai_workflow.usecases.AgentLifecycleUseCase;
import com.hdekker.ai_workflow.test.pipeline.config.ChatClientTestConfig;
import com.hdekker.ai_workflow.test.pipeline.factory.TestConfigurationFactory;

/**
 * Full integration test for the file polling → agent → LLM call flow.
 * 
 * This test verifies the complete end-to-end flow:
 * 1. Spring Boot starts with the full Spring Integration pipeline
 * 2. An agent is registered via AgentLifecycleUseCase (subscribes to fileInboundFluxChannel)
 * 3. Placing a file in the watched directory triggers the flow
 * 4. WatchService/polling detects the file and sends it to fileInboundFluxChannel
 * 5. The agent's flux receives the file, processes it, and calls the LLM mock
 * 6. The output file is written to disk
 * 
 * This test specifically covers the scenario that was failing with:
 * "The [bean 'fileInboundFluxChannel'] doesn't have subscribers to accept messages"
 * 
 * The root cause was that the polling adapter could send a message to fileInboundFluxChannel
 * before the agent subscribed to the flux (which triggers the flow to start).
 */
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
    private ChatClientTestConfig chatClientTestConfig;

    @Autowired
    private AgentLifecycleUseCase dynamicAgentManager;

    @TempDir
    Path tempDir;

    @DynamicPropertySource
    static void registerTempDirProperty(DynamicPropertyRegistry registry) {
        registry.add("scanner.url", () -> "file:/test/project-root");
    }

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

        dynamicAgentManager.addDynamicAgent(agentDef, "/tmp/test-dir");
        log.info("Added dynamic agent: {}", agentDef.title());

        // Wait for the subscription chain to establish
        // The doOnSubscribe in FileSystemRecursiveFileScannerAdapter has a 1-second delay
        // plus additional time for the FluxMessageChannel to be ready
        log.info("Waiting for subscription chain to establish...");
        Thread.sleep(3000);
        log.info("Subscription chain should be established");

        // Place a test file in the watched directory (scanner root is /test/project-root)
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

        dynamicAgentManager.addDynamicAgent(agentDef, "/tmp/test-dir");
        log.info("Added agent: {}", agentDef.title());

        // Wait for the subscription chain to establish
        // The doOnSubscribe has a Mono.delay(Duration.ofSeconds(1))
        log.info("Waiting for subscription chain to establish...");
        Thread.sleep(3000);
        log.info("Subscription chain should be established");

        // Place a file in the watched directory
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
                        "LLM mock was not called - this indicates fileInboundFluxChannel had no subscribers "
                        + "when the file was polled, causing MessageDeliveryException. "
                        + "Expected: agent subscription should trigger doOnSubscribe -> registration.start() -> flow active")
                .isTrue();

        log.info("Subscriber registration test PASSED");
    }
}

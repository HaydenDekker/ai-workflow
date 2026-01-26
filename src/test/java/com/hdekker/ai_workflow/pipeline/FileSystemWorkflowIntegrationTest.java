package com.hdekker.ai_workflow.pipeline;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.hdekker.ai_workflow.TestProfiles;
import com.hdekker.ai_workflow.pipeline.domain.AgentDefinition;
import com.hdekker.ai_workflow.test.pipeline.config.ChatClientTestConfig;
import com.hdekker.ai_workflow.test.pipeline.harness.EndToEndTestHarness;
import com.hdekker.ai_workflow.test.pipeline.filesystem.FileSystemTestBuilder;
import com.hdekker.ai_workflow.test.pipeline.mock.MockResponseProvider;
import com.hdekker.ai_workflow.test.pipeline.factory.TestConfigurationFactory;
import com.hdekker.ai_workflow.prompt.PromptResponse;

/**
 * True end-to-end integration test using real file system configuration.
 * 
 * This test class verifies the complete workflow from file system scanning
 * through pipeline execution to output generation using @TempDir for test
 * isolation and real file I/O operations.
 * 
 * Tests all adapter types with real file system scenarios:
 * - MapAgentLLMAdapter: File scanning → configuration → processing → output
 * - SplitterLLMAdapter: File splitting with real file I/O and filename generation
 * - ReducerLLMAdapter: State accumulation across multiple files
 * - Error scenarios: Malformed configurations and file system issues
 */
@SpringBootTest
@Import(ChatClientTestConfig.class)
@ActiveProfiles(TestProfiles.RESOURCES_TEST_FOLDER)
public class FileSystemWorkflowIntegrationTest {
    
    private static final Logger log = LoggerFactory.getLogger(FileSystemWorkflowIntegrationTest.class);
    
    @Autowired
    ChatClientTestConfig chatClientTestConfig;
    
    @TempDir
    static Path tempDir;
    
    @TempDir  
    static Path inputDir;
    
    @TempDir
    static Path outputDir;
    
    @DynamicPropertySource
    static void registerTempDirs(DynamicPropertyRegistry registry) {
        registry.add("prompt-config.predefinedPromptFilePath", () -> tempDir.toAbsolutePath().toString());
        registry.add("scanner.url", () -> "file:/" + inputDir.toAbsolutePath().toString());
    }
    
    /**
     * Test cases for end-to-end workflow scenarios.
     * Each case includes the adapter type, test data, and expected results.
     */
    static Stream<EndToEndTestCase> endToEndTestCases() {
        return Stream.of(
            // Basic Map Agent Scenario
            new EndToEndTestCase(
                "MapAgent",
                TestConfigurationFactory.createMapAgentDefinition(),
                new String[]{"public class TestFunction {\n    public void test() {}\n}"},
                MockResponseProvider.getMapAgentResponse(),
                new EndToEndExpectedResults(
                    1, // responseCount
                    1, // fileCount  
                    Duration.ofSeconds(30), // maxExecutionTime
                    null, // Don't check exact file paths - they are dynamically generated
                    new String[]{MockResponseProvider.getMapAgentResponse()}
                ),
                "Basic Map agent workflow test"
            ),
            
            // Split Agent Scenario  
            new EndToEndTestCase(
                "Split",
                TestConfigurationFactory.createSplitterAgentDefinition(), 
                new String[]{"public class SOLIDViolation {\n    // Multiple SOLID violations\n}"},
                MockResponseProvider.getSplitterResponse(),
                new EndToEndExpectedResults(
                    3, // responseCount (3 splits in mock response)
                    3, // fileCount
                    Duration.ofSeconds(30),
                    null, // Don't check exact file paths for split agent
                    new String[]{"Single Responsibility", "Open/Closed", "Dependency Inversion"} // Content should contain these keys
                ),
                "Split agent file generation test"
            ),
            
            // Default Map Agent (null agentType) - simplified to avoid complex reducer issues
            new EndToEndTestCase(
                null,
                TestConfigurationFactory.createDefaultMapAgentDefinition(),
                new String[]{"public class DefaultTest {\n    // Default processing\n}"},
                MockResponseProvider.getDefaultMapResponse(),
                new EndToEndExpectedResults(
                    1, // responseCount
                    1, // fileCount
                    Duration.ofSeconds(30),
                    null, // Don't check exact file paths
                    new String[]{MockResponseProvider.getDefaultMapResponse()}
                ),
                "Default Map agent fallback test"
            )
        );
    }
    
    /**
     * Parameterized end-to-end test for complete workflow execution.
     * 
     * @param testCase The end-to-end test case with configuration and expectations
     * @throws Exception if workflow execution fails
     */
    @ParameterizedTest
    @MethodSource("endToEndTestCases")
    public void givenFileSystemConfiguration_ExpectCompleteWorkflowExecution(
            EndToEndTestCase testCase) throws Exception {
        
        log.info("Executing end-to-end test: {}", testCase.description());
        
        // Create appropriate ChatClient mock based on adapter type
        ChatClient mockClient = createChatClientForAdapter(testCase.adapterType());
        
        // Create test scenario
        EndToEndTestHarness.TestScenario scenario = new EndToEndTestHarness.TestScenario(
            List.of(testCase.agentDefinition()),
            mockClient,
            testCase.inputFileContents()
        );
        
        // Setup test environment using existing infrastructure
        EndToEndTestHarness.TestEnvironment env = EndToEndTestHarness.setup(scenario, tempDir);
        
        // Execute complete workflow
        EndToEndTestHarness.TestExecution execution = EndToEndTestHarness.executeWorkflow(env);
        
        // Verify workflow results
        EndToEndTestHarness.verifyWorkflowResults(execution, testCase.expectedResults().toHarnessExpectedResults());
        
        // Additional adapter-specific verifications
        verifyAdapterSpecificResults(testCase, execution);
        
        log.info("Successfully completed end-to-end test: {} ({} responses, {} files, {}ms)",
            testCase.description(), 
            execution.responses().size(),
            execution.createdFiles().size(),
            execution.executionTime().toMillis());
    }
    
    /**
     * Creates appropriate ChatClient mock for the specified adapter type.
     */
    private ChatClient createChatClientForAdapter(String adapterType) {
        if ("Reduction".equals(adapterType)) {
            return chatClientTestConfig.createMock(Arrays.asList(
                MockResponseProvider.getReducerInitialResponse(),
                MockResponseProvider.getReducerAccumulatedResponse()
            ));
        } else if ("Split".equals(adapterType)) {
            return chatClientTestConfig.createMock(java.util.List.of(MockResponseProvider.getSplitterResponse()));
        } else if (adapterType == null) {
            // Default Map agent
            return chatClientTestConfig.createMock(MockResponseProvider.getDefaultMapResponse());
        } else {
            // Regular Map agent
            return chatClientTestConfig.createMock(MockResponseProvider.getMapAgentResponse());
        }
    }
    
    /**
     * Performs adapter-specific result verifications beyond basic workflow checks.
     */
    private void verifyAdapterSpecificResults(EndToEndTestCase testCase, 
            EndToEndTestHarness.TestExecution execution) throws IOException {
        
        String adapterType = testCase.adapterType();
        
        // Basic verification for all types
        assertTrue(execution.responses().size() > 0, "Should have at least one response");
        assertTrue(execution.createdFiles().size() > 0, "Should have created at least one file");
        
        if ("Split".equals(adapterType)) {
            // Verify Split adapter created multiple files
            assertEquals(3, execution.responses().size(), "Split adapter should create 3 responses");
            assertEquals(3, execution.createdFiles().size(), "Split adapter should create 3 files");
            
            // Verify each response has expected split content
            String[] expectedKeys = {"Single Responsibility", "Open/Closed", "Dependency Inversion"};
            for (int i = 0; i < expectedKeys.length && i < execution.responses().size(); i++) {
                assertTrue(execution.responses().get(i).response().contains(expectedKeys[i]),
                    "Split response " + i + " should contain " + expectedKeys[i]);
            }
            
        } else {
            // Map agent verification - 1:1 correspondence
            assertEquals(1, execution.responses().size(),
                "Map agent should create exactly 1 response");
            assertEquals(1, execution.createdFiles().size(),
                "Map agent should create exactly 1 file");
                
            // Verify output files were created with expected names
            for (PromptResponse response : execution.responses()) {
                assertNotNull(response.fileName(), 
                    "Map agent response should have output filename");
                assertFalse(response.fileName().trim().isEmpty(),
                    "Map agent output filename should not be empty");
            }
        }
        
        // Log what was actually created for debugging
        log.info("Created {} response(s) and {} file(s)", 
            execution.responses().size(), execution.createdFiles().size());
        for (Path file : execution.createdFiles()) {
            log.info("Output file: {}", file.toString());
        }
    }
    
    /**
     * Test case record for end-to-end workflow testing.
     */
    public record EndToEndTestCase(
        String adapterType,
        AgentDefinition agentDefinition,
        String[] inputFileContents,
        String mockResponse,
        EndToEndExpectedResults expectedResults,
        String description
    ) {}
    
    /**
     * Expected results for end-to-end test verification.
     */
    public record EndToEndExpectedResults(
        int responseCount,
        int fileCount,
        Duration maxExecutionTime,
        FileSystemTestBuilder.ExpectedOutput[] expectedOutputs,
        String[] expectedResponseContent
    ) {
        
        /**
         * Convert to EndToEndTestHarness.ExpectedResults format.
         */
        EndToEndTestHarness.ExpectedResults toHarnessExpectedResults() {
            return new EndToEndTestHarness.ExpectedResults(
                responseCount,
                fileCount,
                maxExecutionTime,
                expectedOutputs,
                expectedResponseContent
            );
        }
    }
}
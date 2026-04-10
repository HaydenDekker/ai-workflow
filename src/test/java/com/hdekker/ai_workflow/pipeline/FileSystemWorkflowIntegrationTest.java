package com.hdekker.ai_workflow.pipeline;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;
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
import com.hdekker.ai_workflow.test.pipeline.config.ChatClientTestConfig;
import com.hdekker.ai_workflow.test.pipeline.harness.EndToEndTestHarness;

import com.hdekker.ai_workflow.test.pipeline.mock.MockResponseProvider;
import com.hdekker.ai_workflow.test.pipeline.factory.TestConfigurationFactory;

/**
 * True end-to-end integration test using real file system configuration.
 * 
 * This test class verifies the complete workflow from file system scanning
 * through pipeline execution to output generation using @TempDir for test
 * isolation and real file I/O operations, with only the MapAgent adapter type.
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
     * Parameterized end-to-end test for complete workflow execution.
     * 
     * @param testCase The end-to-end test case with configuration and expectations
     * @throws Exception if workflow execution fails
     */
    @Test
    public void givenFileSystemConfiguration_ExpectCompleteWorkflowExecution() throws Exception {

        log.info("Executing basic MapAgent workflow test");
        
        // Create ChatClient mock for MapAgent
        ChatClient mockClient = chatClientTestConfig.createMock(MockResponseProvider.getMapAgentResponse());


        // Create test scenario
        EndToEndTestHarness.TestScenario scenario = new EndToEndTestHarness.TestScenario(
            List.of(TestConfigurationFactory.createMapAgentDefinition()),
            mockClient,
            new String[]{"public class TestFunction {\n    public void test() {}\n}"}
        );

        // Setup test environment using existing infrastructure
        EndToEndTestHarness.TestEnvironment env = EndToEndTestHarness.setup(scenario, tempDir);

        // Execute complete workflow
        EndToEndTestHarness.TestExecution execution = EndToEndTestHarness.executeWorkflow(env);

        // Verify workflow results
        EndToEndTestHarness.verifyWorkflowResults(execution, new EndToEndTestHarness.ExpectedResults(
            1, // responseCount
            1, // fileCount
            Duration.ofSeconds(30), // maxExecutionTime
            null, // Don't check exact file paths - they are dynamically generated
            new String[]{MockResponseProvider.getMapAgentResponse()}
        ));


        // Additional adapter-specific verifications
        // verifyAdapterSpecificResults(execution);

        
        log.info("Successfully completed MapAgent workflow test: {} responses, {} files, {}ms",
            execution.responses().size(),
            execution.createdFiles().size(),
            execution.executionTime().toMillis());
    }

    
}
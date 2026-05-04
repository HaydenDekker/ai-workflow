package com.hdekker.ai_workflow.test.pipeline.harness;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.hdekker.ai_workflow.domain.agent.AgentDefinition;
import com.hdekker.ai_workflow.domain.prompt.PromptResponse;
import com.hdekker.ai_workflow.test.pipeline.factory.TestConfigurationFactory;
import com.hdekker.ai_workflow.test.pipeline.mock.ChatClientMockBuilder;
import com.hdekker.ai_workflow.test.pipeline.mock.MockResponseProvider;

import org.springframework.ai.chat.client.ChatClient;

/**
 * Integration test for EndToEndTestHarness functionality.
 * Verifies test environment setup, workflow execution, and result collection.
 */
class EndToEndTestHarnessTest {

    @TempDir
    Path tempDir;

    private ChatClient mockChatClient;

    @BeforeEach
    void setUp() {
        // Use the existing ChatClientMockBuilder for proper mocking
        mockChatClient = ChatClientMockBuilder.createMock(MockResponseProvider.getMapAgentResponse());
    }

    @Test
    void givenTestScenario_whenSetup_thenCreatesTestEnvironment() throws IOException {
        // Given
        AgentDefinition definition = TestConfigurationFactory.createMapAgentDefinition();
        EndToEndTestHarness.TestScenario scenario = new EndToEndTestHarness.TestScenario(
            List.of(definition),
            mockChatClient,
            new String[]{"public class TestClass { void method() {} }"}
        );

        // When
        EndToEndTestHarness.TestEnvironment env = EndToEndTestHarness.setup(scenario, tempDir);

        // Then
        assertNotNull(env);
        assertEquals(tempDir, env.tempDir());
        assertNotNull(env.directoryStructure());
        assertEquals(1, env.agentDefinitions().size());
        assertEquals(definition, env.agentDefinitions().get(0));
        assertEquals(mockChatClient, env.mockChatClient());
        
        // Verify directory structure exists
        assertTrue(env.directoryStructure().promptConfigDir().toFile().exists());
        assertTrue(env.directoryStructure().inputDir().toFile().exists());
        assertTrue(env.directoryStructure().outputDir().toFile().exists());
        
        // Verify AgentDefinition file was created
        assertTrue(Files.list(env.directoryStructure().promptConfigDir()).findAny().isPresent());
        
        // Verify input file was created
        assertEquals(1, Files.list(env.directoryStructure().inputDir()).count());
    }

    @Test
    void givenTestEnvironment_whenExecuteWorkflow_thenProducesExpectedResults() throws IOException {
        // Given
        AgentDefinition definition = TestConfigurationFactory.createMapAgentDefinition();
        EndToEndTestHarness.TestScenario scenario = new EndToEndTestHarness.TestScenario(
            List.of(definition),
            mockChatClient,
            new String[]{"public class TestClass { void method() {} }"}
        );
        EndToEndTestHarness.TestEnvironment env = EndToEndTestHarness.setup(scenario, tempDir);

        // When
        EndToEndTestHarness.TestExecution execution = EndToEndTestHarness.executeWorkflow(env);

        // Then
        assertNotNull(execution);
        assertEquals(1, execution.responses().size());
        assertEquals(1, execution.createdFiles().size());
        
        // Verify response content
        PromptResponse response = execution.responses().get(0);
        assertEquals(definition, response.prompt());
        assertTrue(response.response().contains("processData"));
        assertTrue(response.fileName().contains("test-file-0.java"));
        assertNotNull(response.fileContents());
        assertEquals("public class TestClass { void method() {} }", response.fileContents());
        
        // Verify output file was created
        Path outputFile = execution.createdFiles().get(0);
        assertTrue(outputFile.toFile().exists());
        assertTrue(Files.readString(outputFile).contains("processData"));
    }

    @Test
    void givenMultipleInputs_whenExecuteWorkflow_thenProcessesAllFiles() throws IOException {
        // Given
        AgentDefinition definition = TestConfigurationFactory.createMapAgentDefinition();
        EndToEndTestHarness.TestScenario scenario = new EndToEndTestHarness.TestScenario(
            List.of(definition),
            mockChatClient,
            new String[]{
                "public class ClassA { void methodA() {} }",
                "public class ClassB { void methodB() {} }"
            }
        );
        EndToEndTestHarness.TestEnvironment env = EndToEndTestHarness.setup(scenario, tempDir);

        // When
        EndToEndTestHarness.TestExecution execution = EndToEndTestHarness.executeWorkflow(env);

        // Then
        assertEquals(2, execution.responses().size());
        assertEquals(2, execution.createdFiles().size());
        
        // Verify each response corresponds to an input file
        for (PromptResponse response : execution.responses()) {
            assertEquals(definition, response.prompt());
            assertTrue(response.response().contains("processData"));
        }
    }

    @Test
    void givenExpectedResults_whenVerifyWorkflowResults_thenValidatesCorrectly() throws IOException {
        // Given
        AgentDefinition definition = TestConfigurationFactory.createMapAgentDefinition();
        EndToEndTestHarness.TestScenario scenario = new EndToEndTestHarness.TestScenario(
            List.of(definition),
            mockChatClient,
            new String[]{"public class TestClass { void method() {} }"}
        );
        EndToEndTestHarness.TestEnvironment env = EndToEndTestHarness.setup(scenario, tempDir);
        EndToEndTestHarness.TestExecution execution = EndToEndTestHarness.executeWorkflow(env);
        
        EndToEndTestHarness.ExpectedResults expected = new EndToEndTestHarness.ExpectedResults(
            1, // responseCount
            1, // fileCount
            Duration.ofSeconds(30), // maxExecutionTime
            null, // expectedOutputs
            new String[]{"processData"} // expectedResponseContent
        );

        // When & Then (should not throw)
        assertDoesNotThrow(() -> EndToEndTestHarness.verifyWorkflowResults(execution, expected));
    }

    @Test
    void givenIncorrectExpectedResults_whenVerifyWorkflowResults_thenThrowsAssertionError() throws IOException {
        // Given
        AgentDefinition definition = TestConfigurationFactory.createMapAgentDefinition();
        EndToEndTestHarness.TestScenario scenario = new EndToEndTestHarness.TestScenario(
            List.of(definition),
            mockChatClient,
            new String[]{"public class TestClass { void method() {} }"}
        );
        EndToEndTestHarness.TestEnvironment env = EndToEndTestHarness.setup(scenario, tempDir);
        EndToEndTestHarness.TestExecution execution = EndToEndTestHarness.executeWorkflow(env);
        
        EndToEndTestHarness.ExpectedResults incorrectExpected = new EndToEndTestHarness.ExpectedResults(
            2, // incorrect responseCount
            1, // fileCount
            Duration.ofSeconds(30), // maxExecutionTime
            null, // expectedOutputs
            null // expectedResponseContent
        );

        // When & Then
        AssertionError exception = assertThrows(AssertionError.class, 
            () -> EndToEndTestHarness.verifyWorkflowResults(execution, incorrectExpected));
        assertTrue(exception.getMessage().contains("Expected 2 responses, but got 1"));
    }

    @Test
    void givenTimeout_whenWaitForProcessing_thenCompletesWithinTime() throws InterruptedException {
        // Given
        Duration timeout = Duration.ofSeconds(5);

        // When & Then (should not throw)
        assertDoesNotThrow(() -> EndToEndTestHarness.waitForProcessing(timeout));
    }

    @Test
    void givenShortTimeout_whenWaitForProcessing_thenThrowsException() {
        // Given
        Duration shortTimeout = Duration.ofMillis(10);

        // When & Then
        assertThrows(RuntimeException.class, 
            () -> EndToEndTestHarness.waitForProcessing(shortTimeout));
    }

    @Test
    void givenSplitterAgent_whenExecuteWorkflow_thenHandlesMultipleResponses() throws IOException {
        // Given
        AgentDefinition definition = TestConfigurationFactory.createSplitterAgentDefinition();
        
        // Setup mock for splitter response using ChatClientMockBuilder
        mockChatClient = ChatClientMockBuilder.createMock(List.of(MockResponseProvider.getSplitterResponse()), null);
        
        EndToEndTestHarness.TestScenario scenario = new EndToEndTestHarness.TestScenario(
            List.of(definition),
            mockChatClient,
            new String[]{"public class BadClass { void multipleResponsibilities() {} }"}
        );
        EndToEndTestHarness.TestEnvironment env = EndToEndTestHarness.setup(scenario, tempDir);

        // When
        EndToEndTestHarness.TestExecution execution = EndToEndTestHarness.executeWorkflow(env);

        // Then
        assertNotNull(execution);
        assertEquals(3, execution.responses().size()); // Splitter creates 3 responses from the split tokens
        assertEquals(3, execution.createdFiles().size());
        
        // Verify responses contain splitter content (each response contains only the content for its key)
        assertTrue(execution.responses().stream().anyMatch(r -> r.response().contains("Class handles multiple responsibilities")));
        assertTrue(execution.responses().stream().anyMatch(r -> r.response().contains("Class requires modification for new features")));
        assertTrue(execution.responses().stream().anyMatch(r -> r.response().contains("Direct dependency on concrete classes")));
    }

    @Test
    void givenNoInputFiles_whenExecuteWorkflow_thenProducesNoResponses() throws IOException {
        // Given
        AgentDefinition definition = TestConfigurationFactory.createMapAgentDefinition();
        EndToEndTestHarness.TestScenario scenario = new EndToEndTestHarness.TestScenario(
            List.of(definition),
            mockChatClient,
            new String[]{} // No input files
        );
        EndToEndTestHarness.TestEnvironment env = EndToEndTestHarness.setup(scenario, tempDir);

        // When
        EndToEndTestHarness.TestExecution execution = EndToEndTestHarness.executeWorkflow(env);

        // Then
        assertEquals(0, execution.responses().size());
        assertEquals(0, execution.createdFiles().size());
        assertTrue(execution.executionTime().toMillis() >= 0);
    }
}
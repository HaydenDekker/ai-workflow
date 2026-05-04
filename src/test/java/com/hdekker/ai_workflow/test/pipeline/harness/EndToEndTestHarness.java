package com.hdekker.ai_workflow.test.pipeline.harness;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import com.hdekker.ai_workflow.domain.agent.AgentDefinition;
import com.hdekker.ai_workflow.domain.prompt.PromptRequest;
import com.hdekker.ai_workflow.domain.prompt.PromptResponse;
import com.hdekker.ai_workflow.pipeline.LLMAdapter;
import com.hdekker.ai_workflow.pipeline.llmadapter.LLMAdapterFactory;
import com.hdekker.ai_workflow.test.pipeline.filesystem.FileSystemTestBuilder;

import org.springframework.ai.chat.client.ChatClient;
import reactor.core.publisher.Flux;

/**
 * Execute and verify complete workflow scenarios.
 * Provides utilities to setup test environments, execute workflows,
 * and collect results for end-to-end testing.
 */
public class EndToEndTestHarness {

    /**
     * Setup complete test environment with file system and mocks.
     * 
     * @param scenario Test scenario configuration
     * @param tempDir Temporary directory for test isolation
     * @return Configured test environment
     * @throws IOException if file system setup fails
     */
    public static TestEnvironment setup(TestScenario scenario, Path tempDir) throws IOException {
        // Setup directory structure
        FileSystemTestBuilder.TestDirectoryStructure directoryStructure = 
            FileSystemTestBuilder.setupDirectoryStructure(tempDir);
        
        // Create AgentDefinition files
        FileSystemTestBuilder.createAgentDefinitionFiles(
            directoryStructure.promptConfigDir(), 
            scenario.agentDefinitions()
        );
        
        if (scenario.inputFileContents() != null) {
             FileSystemTestBuilder.createTestInputFiles(
                directoryStructure.inputDir(), 
                scenario.inputFileContents()
            );
        }
        
        return new TestEnvironment(
            tempDir,
            directoryStructure,
            scenario.agentDefinitions(),
            scenario.mockChatClient()
        );
    }

    /**
     * Execute complete workflow and return results.
     * 
     * @param env Test environment configured by setup()
     * @return Test execution results
     */
    public static TestExecution executeWorkflow(TestEnvironment env) {
        Instant startTime = Instant.now();
        List<PromptResponse> responses = new ArrayList<>();
        List<Path> createdFiles = new ArrayList<>();
        
        try {
            // Process each AgentDefinition
            for (AgentDefinition definition : env.agentDefinitions()) {
                // Create adapter for this definition
                LLMAdapter adapter = LLMAdapterFactory.create(env.mockChatClient(), definition);
                
                // Create PromptRequest from input files
                List<PromptRequest> requests = createPromptRequests(env.directoryStructure(), definition);
                
                // Execute workflow
                Flux<PromptRequest> requestFlux = Flux.fromIterable(requests);
                Flux<PromptResponse> responseFlux = adapter.call(requestFlux);
                
                // Collect responses - PromptResponse objects should work correctly now
                List<PromptResponse> adapterResponses = responseFlux.collectList().block();
                if (adapterResponses != null) {
                    responses.addAll(adapterResponses);
                    
                    // Create output files from responses
                    for (PromptResponse response : adapterResponses) {
                        Path outputFile = createOutputFile(env.directoryStructure(), response);
                        if (outputFile != null) {
                            createdFiles.add(outputFile);
                        }
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Workflow execution failed", e);
        }
        
        Instant endTime = Instant.now();
        Duration executionTime = Duration.between(startTime, endTime);
        
        return new TestExecution(responses, createdFiles, executionTime);
    }

    /**
     * Wait for file processing to complete with timeout.
     * 
     * @param timeout Maximum time to wait
     * @throws InterruptedException if waiting is interrupted
     */
    public static void waitForProcessing(Duration timeout) throws InterruptedException {
        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            try {
                // Simulate processing time - in real implementation this would
                // monitor actual file processing events
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        
        try {
            future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            throw new RuntimeException("Timeout waiting for processing to complete", e);
        }
    }

    /**
     * Verify complete workflow results against expectations.
     * 
     * @param execution Results from executeWorkflow()
     * @param expected Expected results for verification
     * @throws AssertionError if verification fails
     * @throws IOException if file verification fails
     */
    public static void verifyWorkflowResults(TestExecution execution, ExpectedResults expected) throws IOException {
        // Verify response count
        if (expected.responseCount() != execution.responses().size()) {
            throw new AssertionError("Expected " + expected.responseCount() + " responses, but got " + 
                execution.responses().size());
        }
        
        // Verify file count
        if (expected.fileCount() != execution.createdFiles().size()) {
            throw new AssertionError("Expected " + expected.fileCount() + " files, but got " + 
                execution.createdFiles().size());
        }
        
        // Verify execution time if specified
        if (expected.maxExecutionTime() != null && execution.executionTime().compareTo(expected.maxExecutionTime()) > 0) {
            throw new AssertionError("Execution time " + execution.executionTime() + 
                " exceeds maximum " + expected.maxExecutionTime());
        }
        
        // Verify output files if specified
        if (expected.expectedOutputs() != null) {
            FileSystemTestBuilder.verifyOutputFiles(
                execution.createdFiles().get(0).getParent(), 
                expected.expectedOutputs()
            );
        }
        
        // Verify response content if specified
        if (expected.expectedResponseContent() != null) {
            for (int i = 0; i < expected.expectedResponseContent().length && i < execution.responses().size(); i++) {
                String expectedContent = expected.expectedResponseContent()[i];
                String actualContent = execution.responses().get(i).response();
                if (!actualContent.contains(expectedContent)) {
                    throw new AssertionError("Response " + i + " does not contain expected content: " + expectedContent);
                }
            }
        }
    }

    /**
     * Create PromptRequest objects from input files matching the AgentDefinition.
     */
    private static List<PromptRequest> createPromptRequests(
            FileSystemTestBuilder.TestDirectoryStructure structure, 
            AgentDefinition definition) throws IOException {
        
        List<PromptRequest> requests = new ArrayList<>();
        
        // Find matching input files
        if (Files.exists(structure.inputDir())) {
            try (var stream = Files.list(structure.inputDir())) {
                stream.filter(path -> {
                    String fileURL = "file:/" + path.toAbsolutePath().toString();
                    return definition.inputRegexMatches(fileURL);
                })
                .forEach(path -> {
                    try {
                        String content = Files.readString(path);
                        String fileURL = "file:/" + path.toAbsolutePath().toString();
                        requests.add(new PromptRequest(content, fileURL));
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to read input file: " + path, e);
                    }
                });
            }
        }
        
        return requests;
    }

    /**
     * Create output file from PromptResponse.
     */
    private static Path createOutputFile(
            FileSystemTestBuilder.TestDirectoryStructure structure,
            PromptResponse response) {
        
        try {
            String outputFileName = response.createOutputFileName();
            Path outputFile = structure.outputDir().resolve(outputFileName);
            
            // Create parent directories if needed
            Files.createDirectories(outputFile.getParent());
            
            // Write the response content
            Files.writeString(outputFile, response.response());
            
            return outputFile;
        } catch (IOException e) {
            throw new RuntimeException("Failed to create output file for response: " + response, e);
        }
    }

    /**
     * Test scenario configuration for workflow execution.
     */
    public record TestScenario(
        List<AgentDefinition> agentDefinitions,
        ChatClient mockChatClient,
        String[] inputFileContents
    ) {}

    /**
     * Complete test environment with all necessary components.
     */
    public record TestEnvironment(
        Path tempDir,
        FileSystemTestBuilder.TestDirectoryStructure directoryStructure,
        List<AgentDefinition> agentDefinitions,
        ChatClient mockChatClient
    ) {}

    /**
     * Results from workflow execution for verification.
     */
    public record TestExecution(
        List<PromptResponse> responses,
        List<Path> createdFiles,
        Duration executionTime
    ) {}

    /**
     * Expected results for workflow verification.
     */
    public record ExpectedResults(
        int responseCount,
        int fileCount,
        Duration maxExecutionTime,
        FileSystemTestBuilder.ExpectedOutput[] expectedOutputs,
        String[] expectedResponseContent
    ) {}
}
# Test Pipeline Infrastructure Usage Guide

## Overview

The test pipeline infrastructure provides a comprehensive set of tools and utilities specifically designed for testing AI workflow functionality. This infrastructure helps developers create robust, isolated, and maintainable tests for their pipeline components. Think of it as your testing toolkit - everything you need to build reliable tests for AI workflows.

## What is the Test Pipeline Infrastructure?

This infrastructure is essentially a testing framework built on top of JUnit and Spring Boot that provides:

- **File System Isolation**: Create temporary, isolated test environments
- **Mock LLM Services**: Simulate AI model responses without calling real APIs
- **Test Data Creation**: Generate test configurations and scenarios easily
- **Workflow Orchestration**: Execute and verify complete pipeline workflows

## Package Breakdown

### 1. Harness (`test.pipeline.harness`)

**What it does**: The harness is your workflow conductor. It sets up, runs, and verifies complete workflow scenarios from start to finish.

**Key Components**:
- `EndToEndTestHarness` - The main orchestrator for running complete workflow tests

**When to use it**: When you need to test an entire workflow - from file input to final output, including all the processing steps in between.

#### Example:

```java
import com.hdekker.ai_workflow.test.pipeline.harness.EndToEndTestHarness;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.List;

class CompleteWorkflowTest {

    @Test
    void testCompleteWorkflow(@TempDir Path tempDir) throws Exception {
        // 1. Create your test scenario
        EndToEndTestHarness.TestScenario scenario = new EndToEndTestHarness.TestScenario(
            List.of(yourAgentDefinition),     // Your pipeline configuration
            yourMockChatClient,                // Mocked AI service
            new String[]{"input content"}       // Test input files
        );
        
        // 2. Set up the test environment
        EndToEndTestHarness.TestEnvironment env = EndToEndTestHarness.setup(scenario, tempDir);
        
        // 3. Execute the workflow
        EndToEndTestHarness.TestExecution execution = EndToEndTestHarness.executeWorkflow(env);
        
        // 4. Verify everything worked
        EndToEndTestHarness.verifyWorkflowResults(execution, expectedResults);
    }
}
```

### 2. Mock (`test.pipeline.mock`)

**What it does**: The mock package provides tools to simulate AI model responses. This lets you test your pipeline without making expensive or unreliable API calls to real AI services.

**Key Components**:
- `ChatClientMockBuilder` - Builder for creating mock AI clients
- `MockConfiguration` - Configuration for mock behavior (delays, errors, responses)
- `MockResponseProvider` - Pre-built realistic mock responses

**When to use it**: Whenever your test needs to interact with an AI service, or when you want to test how your pipeline handles different types of AI responses.

#### Example 1: Simple Mock

```java
import com.hdekker.ai_workflow.test.pipeline.mock.ChatClientMockBuilder;
import org.springframework.ai.chat.client.ChatClient;

class SimpleMockTest {

    @Test
    void testWithMockResponse() {
        // Create a mock that always responds with "Hello, World!"
        ChatClient mockClient = ChatClientMockBuilder.forMapAdapter("Hello, World!");
        
        // Now use this mockClient in your pipeline test
        // The pipeline will receive "Hello, World!" as the AI response
    }
}
```

#### Example 2: Advanced Mock Configuration

```java
import com.hdekker.ai_workflow.test.pipeline.mock.ChatClientMockBuilder;
import com.hdekker.ai_workflow.test.pipeline.mock.MockConfiguration;
import java.time.Duration;

class AdvancedMockTest {

    @Test
    void testWithRealisticMock() {
        // Create a mock that behaves more like a real AI service
        MockConfiguration config = MockConfiguration.builder()
            .response("This is a thoughtful response")  // The AI response
            .responseDelay(Duration.ofSeconds(2))      // Simulate processing time
            .simulateError(false)                      // Don't throw errors
            .build();
            
        ChatClient mockClient = ChatClientMockBuilder.withConfiguration(config);
        
        // Your pipeline will experience a 2-second delay before getting the response
        // This helps test timeout handling and performance
    }
}
```

#### Example 3: Error Testing

```java
class ErrorHandlingTest {

    @Test
    void testErrorHandling() {
        // Create a mock that simulates an AI service error
        MockConfiguration config = MockConfiguration.builder()
            .simulateError(true)
            .errorMessage("AI service temporarily unavailable")
            .build();
            
        ChatClient mockClient = ChatClientMockBuilder.withConfiguration(config);
        
        // Now you can test how your pipeline handles AI service failures
        // This is crucial for building robust systems
    }
}
```

### 3. Factory (`test.pipeline.factory`)

**What it does**: The factory package creates test data and configurations. Instead of manually creating complex objects for your tests, you can use these factories to generate them consistently.

**Key Components**:
- `TestConfigurationFactory` - Creates test configurations for different adapter types
- `AdapterTestDataProvider` - Provides test data for parameterized tests

**When to use it**: When you need to create agent definitions or test configurations, and you want consistent, reusable test data.

#### Example:

```java
import com.hdekker.ai_workflow.test.pipeline.factory.TestConfigurationFactory;
import com.hdekker.ai_workflow.pipeline.domain.AgentDefinition;

class FactoryUsageTest {

    @Test
    void testWithFactoryData() {
        // Instead of manually creating AgentDefinition:
        AgentDefinition mapAgent = TestConfigurationFactory.createMapAgentDefinition();
        
        // Or for split agents:
        AgentDefinition splitAgent = TestConfigurationFactory.createSplitAgentDefinition();
        
        // Or for reducer agents:
        AgentDefinition reducerAgent = TestConfigurationFactory.createReducerAgentDefinition();
        
        // These come with sensible defaults and are ready to use
        // Much easier than creating them manually!
    }
}
```

### 4. File System (`test.pipeline.filesystem`)

**What it does**: The file system package helps you create and manage test files and directories. It ensures each test runs in isolation without interfering with other tests or your actual files.

**Key Components**:
- `FileSystemTestBuilder` - Creates test directory structures and files
- `YamlTestUtils` - Reads and writes YAML configuration files

**When to use it**: When your pipeline processes files, reads configurations, or writes output files.

#### Example 1: Setting Up Test Files

```java
import com.hdekker.ai_workflow.test.pipeline.filesystem.FileSystemTestBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.List;

class FileSystemTest {

    @Test
    void testWithFileSystem(@TempDir Path tempDir) throws Exception {
        // Create the directory structure your pipeline expects
        FileSystemTestBuilder.TestDirectoryStructure structure = 
            FileSystemTestBuilder.setupDirectoryStructure(tempDir);
        
        // Create test input files
        List<String> contents = List.of("Document 1", "Document 2", "Document 3");
        List<Path> inputFiles = FileSystemTestBuilder.createTestInputFiles(
            structure.inputDir(),  // Use the generated input directory
            contents
        );
        
        // Now your pipeline can process these files
        // The files are automatically cleaned up when the test finishes
    }
}
```

#### Example 2: Working with YAML Files

```java
import com.hdekker.ai_workflow.test.pipeline.filesystem.YamlTestUtils;
import com.hdekker.ai_workflow.pipeline.domain.AgentDefinition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;

class YamlTest {

    @Test
    void testYamlOperations(@TempDir Path tempDir) throws Exception {
        // Create a test configuration
        AgentDefinition config = TestConfigurationFactory.createMapAgentDefinition();
        
        // Write it to a YAML file
        Path yamlFile = YamlTestUtils.writeAgentDefinition(tempDir, config);
        
        // Read it back (simulates loading configuration)
        AgentDefinition loadedConfig = YamlTestUtils.readAgentDefinition(yamlFile);
        
        // Verify the configuration was loaded correctly
        assertEquals(config.getAgentName(), loadedConfig.getAgentName());
    }
}
```

### 5. Config (`test.pipeline.config`)

**What it does**: The config package provides Spring configuration for test environments. It sets up the beans and configuration needed for your tests to run in a Spring context.

**Key Components**:
- `ChatClientTestConfig` - Spring configuration for test ChatClient beans

**When to use it**: When you need to run tests within a Spring application context.

#### Example:

```java
import com.hdekker.ai_workflow.test.pipeline.config.ChatClientTestConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.ai.chat.client.ChatClient;

@SpringBootTest
@Import(ChatClientTestConfig.class)
class SpringContextTest {

    @Autowired
    private ChatClientTestConfig testConfig;

    @Test
    void testWithSpringContext() {
        // Create a mock client using Spring-managed configuration
        ChatClient mockClient = testConfig.createChatClient(
            MockConfiguration.builder()
                .response("Spring integrated response")
                .build()
        );
        
        // Use the mockClient in your Spring-based test
    }
}
```

## Putting It All Together: A Complete Example

Here's how you might use all the components together to test a complete workflow:

```java
import com.hdekker.ai_workflow.test.pipeline.harness.EndToEndTestHarness;
import com.hdekker.ai_workflow.test.pipeline.mock.ChatClientMockBuilder;
import com.hdekker.ai_workflow.test.pipeline.factory.TestConfigurationFactory;
import com.hdekker.ai_workflow.test.pipeline.filesystem.FileSystemTestBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.List;

class CompleteExampleTest {

    @Test
    void testCompleteWorkflow(@TempDir Path tempDir) throws Exception {
        // 1. Create test configuration using the factory
        List<AgentDefinition> definitions = List.of(
            TestConfigurationFactory.createMapAgentDefinition()
        );
        
        // 2. Create a mock AI client using the mock builder
        ChatClient mockClient = ChatClientMockBuilder.forMapAdapter(
            "This is the processed output from the AI"
        );
        
        // 3. Set up the test scenario
        EndToEndTestHarness.TestScenario scenario = new EndToEndTestHarness.TestScenario(
            definitions,
            mockClient,
            new String[] {
                "First document to process",
                "Second document to process"
            }
        );
        
        // 4. Execute the complete workflow using the harness
        EndToEndTestHarness.TestEnvironment env = EndToEndTestHarness.setup(scenario, tempDir);
        EndToEndTestHarness.TestExecution execution = EndToEndTestHarness.executeWorkflow(env);
        
        // 5. Verify the results
        List<String> expectedOutputs = List.of("This is the processed output from the AI");
        EndToEndTestHarness.verifyWorkflowResults(execution, expectedOutputs);
    }
}
```

## Why Use This Infrastructure?

### 1. **Test Isolation**
- Each test gets its own temporary directory
- Mocks don't interfere with real services
- Tests don't affect each other

### 2. **Realistic Testing**
- Mocks can simulate real AI behavior (delays, errors, varying responses)
- File system operations work exactly like in production
- Complete workflow testing, not just individual components

### 3. **Easy Setup**
- Factory methods create test data quickly
- Builder patterns make configuration intuitive
- Standardized patterns reduce boilerplate code

### 4. **Maintainable**
- Clear separation of concerns
- Reusable components
- Consistent testing patterns across the codebase

## When to Create Your Own vs. Use the Infrastructure

### Use the Infrastructure When:
- Testing pipeline workflows
- Need to mock AI services
- Working with file processing
- Running integration tests

### Create Your Own When:
- Testing business logic that doesn't involve the pipeline
- Unit testing individual utility methods
- Testing database operations
- Testing REST API endpoints

## Common Testing Scenarios

### Scenario 1: Testing New Pipeline Configuration
```java
@Test
void testNewConfiguration(@TempDir Path tempDir) throws Exception {
    // Create your custom configuration
    AgentDefinition customConfig = AgentDefinition.builder()
        .agentType("MyCustomAdapter")
        .agentName("Custom Test")
        .prompt("Custom prompt")
        .build();
    
    // Test it with the infrastructure
    ChatClient mockClient = ChatClientMockBuilder.forMapAdapter("Custom response");
    
    EndToEndTestHarness.TestScenario scenario = new EndToEndTestHarness.TestScenario(
        List.of(customConfig),
        mockClient,
        new String[]{"Test input"}
    );
    
    // Execute and verify
    EndToEndTestHarness.TestEnvironment env = EndToEndTestHarness.setup(scenario, tempDir);
    EndToEndTestHarness.TestExecution execution = EndToEndTestHarness.executeWorkflow(env);
    
    // Verify your custom logic works
    assertTrue(execution.getResults().contains("Custom response"));
}
```

### Scenario 2: Testing Error Recovery
```java
@Test
void testErrorRecovery(@TempDir Path tempDir) throws Exception {
    // Create a mock that fails on first call, succeeds on second
    MockConfiguration config = MockConfiguration.builder()
        .simulateError(true)
        .errorMessage("Service temporarily unavailable")
        .build();
    
    ChatClient mockClient = ChatClientMockBuilder.withConfiguration(config);
    
    // Test how your pipeline handles the error
    // Does it retry? Does it fail gracefully? Does it log appropriately?
}
```

### Scenario 3: Performance Testing
```java
@Test
void testPerformance(@TempDir Path tempDir) throws Exception {
    // Create a mock with realistic delay
    MockConfiguration config = MockConfiguration.builder()
        .responseDelay(Duration.ofMillis(500))
        .build();
    
    ChatClient mockClient = ChatClientMockBuilder.withConfiguration(config);
    
    long startTime = System.currentTimeMillis();
    
    // Run workflow
    EndToEndTestHarness.TestScenario scenario = new EndToEndTestHarness.TestScenario(
        List.of(TestConfigurationFactory.createMapAgentDefinition()),
        mockClient,
        new String[]{"Performance test input"}
    );
    
    EndToEndTestHarness.TestEnvironment env = EndToEndTestHarness.setup(scenario, tempDir);
    EndToEndTestHarness.executeWorkflow(env);
    
    long duration = System.currentTimeMillis() - startTime;
    
    // Verify performance requirements
    assertTrue(duration < 2000, "Workflow should complete within 2 seconds");
}
```

## Getting Started

1. **Import the packages**: Add imports for the components you need
2. **Use @TempDir**: Add `@TempDir Path tempDir` to your test methods for file isolation
3. **Create mocks**: Use `ChatClientMockBuilder` to create mock AI clients
4. **Set up scenarios**: Use `EndToEndTestHarness.TestScenario` to define your test
5. **Execute and verify**: Use `EndToEndTestHarness` to run and verify results

Remember: The test infrastructure is here to make your testing life easier. Start simple, and gradually use more advanced features as you need them. The goal is to write tests that are reliable, fast, and provide confidence that your AI workflow works correctly.
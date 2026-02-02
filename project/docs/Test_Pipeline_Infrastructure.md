# Test Pipeline Infrastructure Use Cases

## Overview

The `test.pipeline` package provides a comprehensive test infrastructure framework for AI workflow pipeline testing. This document demonstrates practical use cases and patterns for leveraging the refactored test infrastructure.

## Package Structure

```
src/test/java/com/hdekker/ai_workflow/test/pipeline/
├── harness/          # Test execution orchestration
├── mock/             # LLM mocking and simulation
├── factory/          # Test data creation and configuration
├── filesystem/       # File system testing utilities
└── config/           # Spring test configuration
```

## Use Case 1: End-to-End Workflow Testing

### Scenario: Testing a Multi-Adapter Pipeline

```java
import com.hdekker.ai_workflow.test.pipeline.harness.EndToEndTestHarness;
import com.hdekker.ai_workflow.test.pipeline.mock.ChatClientMockBuilder;
import com.hdekker.ai_workflow.test.pipeline.factory.TestConfigurationFactory;
import com.hdekker.ai_workflow.test.pipeline.filesystem.FileSystemTestBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.List;

class MultiAdapterWorkflowTest {

    @Test
    void testMapAgentWorkflow(@TempDir Path tempDir) throws Exception {
        // Create mock ChatClient with specific response
        ChatClient mockClient = ChatClientMockBuilder.forMapAdapter("Generated response content");
        
        // Create test configuration
        List<AgentDefinition> definitions = List.of(
            TestConfigurationFactory.createMapAgentDefinition()
        );
        
        // Setup test scenario
        EndToEndTestHarness.TestScenario scenario = new EndToEndTestHarness.TestScenario(
            definitions, 
            mockClient, 
            new String[]{"test input content"}
        );
        
        // Execute workflow
        EndToEndTestHarness.TestEnvironment env = EndToEndTestHarness.setup(scenario, tempDir);
        EndToEndTestHarness.TestExecution execution = EndToEndTestHarness.executeWorkflow(env);
        
        // Verify results
        EndToEndTestHarness.verifyWorkflowResults(execution, List.of("Generated response content"));
    }
}
```

### Key Benefits:
- **Isolated file system** using `@TempDir`
- **Reusable mock configurations** via `ChatClientMockBuilder`
- **Comprehensive test data** from `TestConfigurationFactory`
- **Complete workflow verification** with `EndToEndTestHarness`

## Use Case 2: Parameterized Testing with Different Adapter Types

### Scenario: Testing All LLM Adapter Types

```java
import com.hdekker.ai_workflow.test.pipeline.factory.AdapterTestDataProvider;
import com.hdekker.ai_workflow.test.pipeline.factory.AdapterTestCase;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import java.util.stream.Stream;

class AdapterParameterizedTest {

    static Stream<AdapterTestCase> adapterTestCases() {
        return AdapterTestDataProvider.provideAllAdapterTypes();
    }

    @ParameterizedTest
    @MethodSource("adapterTestCases")
    void testAllAdapterTypes(AdapterTestCase testCase) {
        // Test case contains:
        // - AgentDefinition for the adapter type
        // - MockConfiguration for expected behavior
        // - Expected outputs
        // - Test description
        
        // Execute with provided test case
        testAdapterWithConfiguration(testCase);
    }
}
```

### Key Benefits:
- **Single test method** for all adapter types
- **Comprehensive test coverage** with varied scenarios
- **Maintainable test data** via `AdapterTestDataProvider`

## Use Case 3: Custom Mock Configuration

### Scenario: Testing Error Handling and Edge Cases

```java
import com.hdekker.ai_workflow.test.pipeline.mock.ChatClientMockBuilder;
import com.hdekker.ai_workflow.test.pipeline.mock.MockConfiguration;
import org.junit.jupiter.api.Test;

class ErrorHandlingTest {

    @Test
    void testTimeoutHandling() {
        // Create mock that simulates timeout
        MockConfiguration config = MockConfiguration.builder()
            .simulateTimeout(true)
            .timeoutDuration(Duration.ofSeconds(30))
            .build();
            
        ChatClient mockClient = ChatClientMockBuilder.withConfiguration(config);
        // Test timeout handling logic
    }

    @Test
    void testErrorResponses() {
        // Create mock that returns errors
        MockConfiguration config = MockConfiguration.builder()
            .simulateError(true)
            .errorMessage("Simulated LLM error")
            .build();
            
        ChatClient mockClient = ChatClientMockBuilder.withConfiguration(config);
        // Test error handling logic
    }

    @Test
    void testDelayedResponses() {
        // Create mock with response delays
        MockConfiguration config = MockConfiguration.builder()
            .responseDelay(Duration.ofMillis(500))
            .build();
            
        ChatClient mockClient = ChatClientMockBuilder.withConfiguration(config);
        // Test performance with delays
    }
}
```

### Key Benefits:
- **Realistic error simulation** via `MockConfiguration`
- **Timing behavior testing** with configurable delays
- **Builder pattern** for flexible mock setup

## Use Case 4: File System Testing

### Scenario: Testing File Processing Pipelines

```java
import com.hdekker.ai_workflow.test.pipeline.filesystem.FileSystemTestBuilder;
import com.hdekker.ai_workflow.test.pipeline.filesystem.YamlTestUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.List;

class FileSystemProcessingTest {

    @Test
    void testYamlConfigurationProcessing(@TempDir Path tempDir) throws Exception {
        // Create test file system structure
        FileSystemTestBuilder.TestDirectoryStructure structure = 
            FileSystemTestBuilder.setupDirectoryStructure(tempDir);
        
        // Create test YAML configurations
        AgentDefinition testDefinition = TestConfigurationFactory.createSplitAgentDefinition();
        Path yamlFile = YamlTestUtils.writeAgentDefinition(
            structure.promptConfigDir(), 
            testDefinition
        );
        
        // Verify YAML content
        AgentDefinition loadedDefinition = YamlTestUtils.readAgentDefinition(yamlFile);
        assertEquals(testDefinition.getAgentType(), loadedDefinition.getAgentType());
    }

    @Test
    void testInputFileProcessing(@TempDir Path tempDir) throws Exception {
        // Create test input files
        List<String> testContents = List.of(
            "First document content",
            "Second document content",
            "Third document content"
        );
        
        List<Path> inputFiles = FileSystemTestBuilder.createTestInputFiles(
            tempDir, 
            testContents
        );
        
        // Verify file creation
        assertEquals(3, inputFiles.size());
        for (Path file : inputFiles) {
            assertTrue(Files.exists(file));
        }
    }
}
```

### Key Benefits:
- **Isolated test environments** with temporary directories
- **YAML serialization testing** via `YamlTestUtils`
- **File system validation** utilities

## Use Case 5: Integration Testing with Spring Context

### Scenario: Testing with Full Application Context

```java
import com.hdekker.ai_workflow.test.pipeline.config.ChatClientTestConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@Import(ChatClientTestConfig.class)
@ActiveProfiles("test")
class SpringIntegrationTest {

    @Autowired
    private ChatClientTestConfig testConfig;

    @Test
    void testSpringContextIntegration() {
        // Create mocks using Spring-managed beans
        ChatClient mockClient = testConfig.createChatClient(
            MockConfiguration.builder()
                .response("Spring integrated response")
                .build()
        );
        
        // Test with full Spring context
    }
}
```

### Key Benefits:
- **Spring integration** with `ChatClientTestConfig`
- **Prototype-scoped beans** for flexible mock creation
- **Context-aware testing** with proper dependency injection

## Use Case 6: Performance Testing

### Scenario: Load Testing with Multiple Concurrent Workflows

```java
import com.hdekker.ai_workflow.test.pipeline.harness.EndToEndTestHarness;
import com.hdekker.ai_workflow.test.pipeline.factory.AdapterTestDataProvider;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

class PerformanceTest {

    @Test
    void testConcurrentWorkflows(@TempDir Path tempDir) throws Exception {
        int numberOfWorkflows = 10;
        ExecutorService executor = Executors.newFixedThreadPool(5);
        
        List<CompletableFuture<Void>> futures = IntStream.range(0, numberOfWorkflows)
            .mapToObj(i -> CompletableFuture.runAsync(() -> {
                try {
                    // Create temporary directory for each workflow
                    Path workflowTempDir = Files.createTempDirectory(tempDir, "workflow-" + i);
                    
                    // Setup and execute workflow
                    EndToEndTestHarness.TestScenario scenario = new EndToEndTestHarness.TestScenario(
                        List.of(TestConfigurationFactory.createMapAgentDefinition()),
                        ChatClientMockBuilder.forMapAdapter("Response " + i),
                        new String[]{"Test input " + i}
                    );
                    
                    EndToEndTestHarness.TestEnvironment env = EndToEndTestHarness.setup(scenario, workflowTempDir);
                    EndToEndTestHarness.TestExecution execution = EndToEndTestHarness.executeWorkflow(env);
                    
                    // Verify results
                    assertEquals(1, execution.getResults().size());
                    
                } catch (Exception e) {
                    fail("Workflow " + i + " failed: " + e.getMessage());
                }
            }, executor))
            .toList();
            
        // Wait for all workflows to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get();
        
        executor.shutdown();
    }
}
```

### Key Benefits:
- **Concurrent execution testing** with thread pool management
- **Resource isolation** with separate temporary directories
- **Scalability validation** for multiple workflow instances

## Use Case 7: Custom Adapter Testing

### Scenario: Testing New Adapter Implementations

```java
import com.hdekker.ai_workflow.test.pipeline.factory.AdapterTestCase;
import com.hdekker.ai_workflow.test.pipeline.mock.MockResponseProvider;
import org.junit.jupiter.api.Test;

class CustomAdapterTest {

    @Test
    void testCustomAdapterBehavior() {
        // Create test case for custom adapter
        AgentDefinition customDefinition = AgentDefinition.builder()
            .agentType("CustomAdapter")
            .agentName("Test Custom Adapter")
            .prompt("Custom test prompt")
            .outputStructure("output-${key}")
            .build();

        MockConfiguration mockConfig = MockConfiguration.builder()
            .responsePattern("--- custom-key ---\nCustom response")
            .build();

        ChatClient mockClient = ChatClientMockBuilder.withConfiguration(mockConfig);

        // Test custom adapter logic
        AdapterTestCase testCase = new AdapterTestCase(
            customDefinition,
            mockConfig,
            List.of("output-custom-key"),
            "Custom adapter test"
        );

        testCustomAdapter(testCase);
    }
}
```

### Key Benefits:
- **Extensible framework** for new adapter types
- **Consistent testing patterns** across all adapters
- **Custom response simulation** with configurable patterns

## Best Practices

### 1. Use the Builder Pattern for Mocks
```java
// Good: Use ChatClientMockBuilder for complex scenarios
ChatClient mockClient = ChatClientMockBuilder.builder()
    .withResponse("Custom response")
    .withDelay(Duration.ofMillis(100))
    .withErrorSimulation(false)
    .build();

// Avoid: Manual mock setup
ChatClient mockClient = mock(ChatClient.class);
// Complex manual setup...
```

### 2. Leverage Test Data Factories
```java
// Good: Use TestConfigurationFactory
AgentDefinition definition = TestConfigurationFactory.createMapAgentDefinition();

// Avoid: Manual definition creation
AgentDefinition definition = AgentDefinition.builder()
    // ... verbose setup
    .build();
```

### 3. Use Parameterized Tests for Coverage
```java
// Good: Parameterized testing
@ParameterizedTest
@MethodSource("testCases")
void testAllScenarios(AdapterTestCase testCase) { ... }

// Avoid: Duplicate test methods
@Test void testCase1() { ... }
@Test void testCase2() { ... }
@Test void testCase3() { ... }
```

### 4. Ensure Test Isolation
```java
// Good: Use @TempDir for each test
@Test
void testIsolatedScenario(@TempDir Path tempDir) { ... }

// Avoid: Shared directories or static resources
```

### 5. Verify End-to-End Results
```java
// Good: Complete verification
EndToEndTestHarness.verifyWorkflowResults(execution, expectedResults);

// Avoid: Partial verification or manual result checking
```

## Migration Guide

If you're migrating from the old `pipeline.support` package, here's the mapping:

| Old Package | New Package |
|-------------|-------------|
| `pipeline.support.EndToEndTestHarness` | `test.pipeline.harness.EndToEndTestHarness` |
| `pipeline.support.ChatClientMockBuilder` | `test.pipeline.mock.ChatClientMockBuilder` |
| `pipeline.support.TestConfigurationFactory` | `test.pipeline.factory.TestConfigurationFactory` |
| `pipeline.support.FileSystemTestBuilder` | `test.pipeline.filesystem.FileSystemTestBuilder` |
| `pipeline.support.ChatClientTestConfig` | `test.pipeline.config.ChatClientTestConfig` |

## Common Pitfalls and Solutions

### 1. Missing Imports
**Problem**: Compilation errors after migration  
**Solution**: Ensure all new package imports are updated

### 2. Static Method Usage
**Problem**: Tests still using static factory methods incorrectly  
**Solution**: Use the new builder patterns and factory methods

### 3. Cross-Package Dependencies
**Problem**: Internal package references not updated  
**Solution**: Check all imports in moved files and update cross-references

### 4. Test Configuration
**Problem**: Spring context configuration not loading new classes  
**Solution**: Update `@Import` statements and component scanning

## Conclusion

The refactored test pipeline infrastructure provides:

- **Better organization** with clear separation of concerns
- **Improved reusability** through builder patterns and factories
- **Enhanced maintainability** with logical package structure
- **Comprehensive testing capabilities** for all pipeline scenarios
- **Scalable framework** for future test infrastructure needs

By following these use cases and best practices, you can create robust, maintainable tests for your AI workflow pipeline infrastructure.
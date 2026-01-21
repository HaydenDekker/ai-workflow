# Test Package Refactoring Plan

## Overview

This document outlines the refactoring of the `com.hdekker.ai_workflow.pipeline.support` package, which has grown beyond its original "support" role and now serves as a comprehensive test infrastructure framework for the AI workflow pipeline.

## Current State Analysis

The `pipeline.support` package contains 13 classes that provide complete test infrastructure for LLM adapter testing:

### Core Components
- **EndToEndTestHarness** - Main workflow execution orchestrator
- **FileSystemTestBuilder** - Test environment and directory structure creator
- **ChatClientMockBuilder** - Sophisticated ChatClient mock creator
- **TestConfigurationFactory** - AgentDefinition test data factory
- **AdapterTestDataProvider** - Parameterized test data provider
- **MockConfiguration** - Mock behavior configuration
- **AdapterTestCase** - Test case data structure
- **MockResponseProvider** - Realistic mock response generator
- **YamlTestUtils** - YAML serialization utilities
- **ChatClientTestConfig** - Spring test configuration

### Current Usage
- **5 integration test classes** import from this package
- **13 total classes** in a flat package structure
- **Multiple responsibilities** mixed together (mocking, file system, data factories, execution)

## Problems with Current Structure

1. **Misleading name** - "support" doesn't reflect its comprehensive test infrastructure role
2. **Flat organization** - All classes in one package despite different responsibilities
3. **Poor discoverability** - Difficult to find specific types of test infrastructure
4. **Limited scalability** - No clear pattern for adding new test components
5. **Confusing purpose** - Not clear that this is specifically for pipeline testing

## Proposed New Structure

### New Package Name
**`com.hdekker.ai_workflow.test.pipeline`**

This name better reflects:
- Test infrastructure (not just support)
- Pipeline-specific focus
- Logical hierarchy under main `test` package
- Room for other test infrastructure (`test.files`, `test.prompt`)

### Subpackage Organization

```
src/test/java/com/hdekker/ai_workflow/test/pipeline/
├── harness/
│   └── EndToEndTestHarness.java
├── mock/
│   ├── ChatClientMockBuilder.java
│   ├── MockConfiguration.java
│   └── MockResponseProvider.java
├── factory/
│   ├── TestConfigurationFactory.java
│   ├── AdapterTestDataProvider.java
│   └── AdapterTestCase.java
├── filesystem/
│   ├── FileSystemTestBuilder.java
│   └── YamlTestUtils.java
└── config/
    └── ChatClientTestConfig.java
```

#### Responsibility-Based Organization

1. **test.pipeline.harness** - Test Execution Framework
   - High-level test scenario execution and verification
   - Workflow orchestration and result collection

2. **test.pipeline.mock** - Mock Infrastructure
   - LLM mocking and response simulation
   - Adapter-specific mock configurations

3. **test.pipeline.factory** - Test Data Creation
   - Test data generation and configuration
   - Parameterized test support

4. **test.pipeline.filesystem** - File System Testing
   - File system isolation and test environment setup
   - Directory structure and file management

5. **test.pipeline.config** - Test Configuration
   - Spring context and bean configuration
   - Test framework setup

## Migration Plan

### Phase 1: Create New Package Structure

**Commands:**
```bash
mkdir -p src/test/java/com/hdekker/ai_workflow/test/pipeline/harness
mkdir -p src/test/java/com/hdekker/ai_workflow/test/pipeline/mock
mkdir -p src/test/java/com/hdekker/ai_workflow/test/pipeline/factory
mkdir -p src/test/java/com/hdekker/ai_workflow/test/pipeline/filesystem
mkdir -p src/test/java/com/hdekker/ai_workflow/test/pipeline/config
```

**File Moves:**
```bash
# Move to harness/
mv src/test/java/com/hdekker/ai_workflow/pipeline/support/EndToEndTestHarness.java \
   src/test/java/com/hdekker/ai_workflow/test/pipeline/harness/

# Move to mock/
mv src/test/java/com/hdekker/ai_workflow/pipeline/support/ChatClientMockBuilder.java \
   src/test/java/com/hdekker/ai_workflow/test/pipeline/mock/
mv src/test/java/com/hdekker/ai_workflow/pipeline/support/MockConfiguration.java \
   src/test/java/com/hdekker/ai_workflow/test/pipeline/mock/
mv src/test/java/com/hdekker/ai_workflow/pipeline/support/MockResponseProvider.java \
   src/test/java/com/hdekker/ai_workflow/test/pipeline/mock/

# Move to factory/
mv src/test/java/com/hdekker/ai_workflow/pipeline/support/TestConfigurationFactory.java \
   src/test/java/com/hdekker/ai_workflow/test/pipeline/factory/
mv src/test/java/com/hdekker/ai_workflow/pipeline/support/AdapterTestDataProvider.java \
   src/test/java/com/hdekker/ai_workflow/test/pipeline/factory/
mv src/test/java/com/hdekker/ai_workflow/pipeline/support/AdapterTestCase.java \
   src/test/java/com/hdekker/ai_workflow/test/pipeline/factory/

# Move to filesystem/
mv src/test/java/com/hdekker/ai_workflow/pipeline/support/FileSystemTestBuilder.java \
   src/test/java/com/hdekker/ai_workflow/test/pipeline/filesystem/
mv src/test/java/com/hdekker/ai_workflow/pipeline/support/YamlTestUtils.java \
   src/test/java/com/hdekker/ai_workflow/test/pipeline/filesystem/

# Move to config/
mv src/test/java/com/hdekker/ai_workflow/pipeline/support/ChatClientTestConfig.java \
   src/test/java/com/hdekker/ai_workflow/test/pipeline/config/
```

### Phase 2: Update Package Declarations

**Files to Update (13 classes):**
- Update package declarations in all moved files
- Update internal imports between classes

**Example Package Declaration Updates:**
```java
// EndToEndTestHarness.java
package com.hdekker.ai_workflow.test.pipeline.harness;

// ChatClientMockBuilder.java  
package com.hdekker.ai_workflow.test.pipeline.mock;

// TestConfigurationFactory.java
package com.hdekker.ai_workflow.test.pipeline.factory;

// FileSystemTestBuilder.java
package com.hdekker.ai_workflow.test.pipeline.filesystem;

// ChatClientTestConfig.java
package com.hdekker.ai_workflow.test.pipeline.config;
```

### Phase 3: Update Import Statements

**Integration Test Files to Update:**
1. `FileSystemWorkflowIntegrationTest.java`
2. `WorkflowIntegrationTest.java`
3. `BuilderPatternIntegrationTest.java`
4. `EndToEndTestHarnessTest.java`
5. `FileSystemTestBuilderTest.java`
6. `YamlTestUtilsTest.java`
7. `ChatClientMockBuilderTest.java`

**Example Import Updates:**
```java
// Old imports
import com.hdekker.ai_workflow.pipeline.support.EndToEndTestHarness;
import com.hdekker.ai_workflow.pipeline.support.ChatClientMockBuilder;
import com.hdekker.ai_workflow.pipeline.support.TestConfigurationFactory;

// New imports
import com.hdekker.ai_workflow.test.pipeline.harness.EndToEndTestHarness;
import com.hdekker.ai_workflow.test.pipeline.mock.ChatClientMockBuilder;
import com.hdekker.ai_workflow.test.pipeline.factory.TestConfigurationFactory;
```

### Phase 4: Update Cross-References Within Package

**Internal Dependencies to Update:**
- `EndToEndTestHarness` → `FileSystemTestBuilder`, `TestConfigurationFactory`
- `ChatClientMockBuilder` → `MockConfiguration`, `MockResponseProvider`
- `AdapterTestCase` → `TestConfigurationFactory`, `MockResponseProvider`

### Phase 5: Clean Up

**Commands:**
```bash
# Remove empty directories
rmdir src/test/java/com/hdekker/ai_workflow/pipeline/support

# Verify no references remain
grep -r "pipeline\.support" src/test/java/
```

### Phase 6: Update Documentation

**Files to Update:**
- `AGENTS.md` - Update build/test commands and package references
- Add this `TEST_PACKAGE_REFACTOR.md` to `plans/` directory

## How to Use Instructions - Modifying Test Structure

### Adding New Test Infrastructure

1. **For new test harnesses or execution frameworks**: Add to `test.pipeline.harness`
2. **For new mock builders or response generators**: Add to `test.pipeline.mock`
3. **For new test data factories or providers**: Add to `test.pipeline.factory`
4. **For file system or environment setup**: Add to `test.pipeline.filesystem`
5. **For Spring configuration or test beans**: Add to `test.pipeline.config`

### Guidelines for Extension

1. **Keep responsibilities clear**: Each subpackage has a specific focus
2. **Follow existing patterns**: Use builder patterns, factory methods, and records
3. **Maintain test isolation**: Use the provided file system and mock infrastructure
4. **Support parameterized testing**: Leverage the factory pattern for test data streams
5. **Document adapter-specific behavior**: Follow the pattern of specialized methods for Map, Split, and Reducer adapters

### Usage Examples

#### Using the New Structure in Tests
```java
import com.hdekker.ai_workflow.test.pipeline.harness.EndToEndTestHarness;
import com.hdekker.ai_workflow.test.pipeline.mock.ChatClientMockBuilder;
import com.hdekker.ai_workflow.test.pipeline.factory.TestConfigurationFactory;
import com.hdekker.ai_workflow.test.pipeline.filesystem.FileSystemTestBuilder;

@Test
void testWorkflowWithNewStructure() {
    // Use mock builder for LLM simulation
    ChatClient mockClient = ChatClientMockBuilder.forMapAdapter("Test response");
    
    // Use factory for test configuration
    AgentDefinition definition = TestConfigurationFactory.createMapAgentDefinition();
    
    // Use harness for end-to-end execution
    EndToEndTestHarness.TestScenario scenario = new EndToEndTestHarness.TestScenario(
        List.of(definition), mockClient, new String[]{"test input"}
    );
    
    // Execute workflow
    Path tempDir = Files.createTempDirectory("test");
    EndToEndTestHarness.TestEnvironment env = EndToEndTestHarness.setup(scenario, tempDir);
    EndToEndTestHarness.TestExecution execution = EndToEndTestHarness.executeWorkflow(env);
    
    // Verify results
    EndToEndTestHarness.verifyWorkflowResults(execution, expectedResults);
}
```

## Benefits of New Structure

1. **Clear separation of concerns** - Each subpackage has a single responsibility
2. **Easier navigation** - Developers can quickly find the type of test infrastructure they need
3. **Scalable** - Room for growth without creating monolithic packages
4. **Reusable patterns** - Other parts of the codebase can adopt similar test infrastructure patterns
5. **Better discoverability** - New developers can understand the testing architecture more quickly
6. **Logical organization** - Related functionality is grouped together
7. **Maintainability** - Easier to locate and modify specific types of test infrastructure

## Risk Mitigation

1. **Incremental migration** - Move one subpackage at a time
2. **Maintain test coverage** - All existing tests continue to work during migration
3. **Update documentation** - Update AGENTS.md with new package structure
4. **Verify integration** - Run full test suite after each migration step
5. **Backup current state** - Ensure ability to rollback if issues arise

## Validation Checklist

- [ ] All 13 classes moved to correct subpackages
- [ ] Package declarations updated in all files
- [ ] Import statements updated in all 7 integration test files
- [ ] Internal cross-references updated
- [ ] All tests pass after migration
- [ ] No references to old `pipeline.support` package remain
- [ ] AGENTS.md updated with new package structure
- [ ] Documentation reflects new organization

## Future Considerations

1. **Additional test infrastructure** - Consider `test.files` and `test.prompt` packages for other test needs
2. **Testing patterns** - This structure can serve as a template for other test infrastructure organization
3. **Performance testing** - Consider dedicated performance test infrastructure if needed
4. **Test utilities** - Common test utilities could be organized in a shared `test.common` package

This refactoring will create a more maintainable, scalable, and understandable test infrastructure that better supports the development and testing of the AI workflow pipeline.
# Test-Specific Builder Pattern Refactor Plan

## Overview
Refactor the current profile-based @Primary ChatClient testing approach to use a dynamic test-specific builder pattern that enables flexible mock configuration for multiple LLM adapters.

## Current Limitations
- Single @Primary ChatClient bean constraint per test context
- Static mock configuration via profiles
- Limited ability to test multiple adapters simultaneously
- Tight coupling to @ActiveProfiles in tests

## Target Architecture
Implement a dynamic mock configuration system using builder pattern with parameterized tests for comprehensive LLM adapter testing.

## Implementation Plan

### Phase 1: Create Core Builder Infrastructure
- [ ] Create `ChatClientMockBuilder` class with fluent API
- [ ] Implement adapter-specific mock configuration methods
- [ ] Add support for dynamic response sequences
- [ ] Create mock behavior presets (success, error, timeout)

### Phase 2: Test Configuration Setup  
- [ ] Create `ChatClientTestConfig` with prototype-scoped beans
- [ ] Remove @Primary from existing `PromptPipelineTestConfig`
- [ ] Implement `@TestConfiguration` classes for complex scenarios
- [ ] Add bean factory methods for different mock types

### Phase 3: Parameterized Test Enhancement
- [ ] Extend `AdapterTestCase` to include mock configuration data
- [ ] Create test data factory for complex adapter interactions
- [ ] Implement parameterized tests with dynamic mock setup
- [ ] Add tests for multi-adapter workflows

### Phase 4: Migration & Cleanup
- [ ] Update existing tests to use builder pattern
- [ ] Remove profile dependencies from test classes
- [ ] Deprecate old profile-based test configuration
- [ ] Update documentation and test examples

### Phase 5: Advanced Features
- [ ] Add support for streaming response mocks
- [ ] Implement error injection for resilience testing  
- [ ] Create mock configuration DSL for complex scenarios
- [ ] Add performance benchmarking capabilities

## Detailed Implementation

### 1. Core Classes to Create

#### `ChatClientMockBuilder`
```java
public class ChatClientMockBuilder {
    public static ChatClient forMapAdapter(String... responses);
    public static ChatClient forSplitterAdapter(List<String> responses);
    public static ChatClient forReducerAdapter(List<String> accumulatedResponses);
    public static ChatClient withErrorBehavior(Class<? extends Exception> error);
    public static ChatClient withTimeoutBehavior(long timeoutMs);
}
```

#### `ChatClientTestConfig`
```java
@TestConfiguration
public class ChatClientTestConfig {
    @Bean
    @Scope("prototype")
    public ChatClientMockBuilder mockChatClientBuilder();
    
    @Bean
    @Scope("prototype") 
    public MockResponseProvider mockResponseProvider();
}
```

#### `MockConfiguration`
```java
public class MockConfiguration {
    private final List<String> responses;
    private final MockBehavior behavior;
    private final Map<String, Object> properties;
    
    // Builder pattern implementation
}
```

### 2. Test Data Enhancements

#### Extended `AdapterTestCase`
```java
public class AdapterTestCase {
    // Existing fields...
    private final MockConfiguration mockConfig;
    private final List<AdapterTestCase> chainedAdapters;
    
    // Factory methods for complex scenarios
    public static AdapterTestCase forMapAdapterWithResponses(String... responses);
    public static AdapterTestCase forSplitterWorkflow(List<String> splitResponses);
    public static AdapterTestCase forReducerChain(List<List<String>> accumulatedResponses);
}
```

#### `AdapterTestDataProvider`
```java
public class AdapterTestDataProvider {
    public static Stream<AdapterTestCase> singleAdapterTests();
    public static Stream<AdapterTestCase> multiAdapterWorkflows();
    public static Stream<AdapterTestCase> errorScenarios();
    public static Stream<AdapterTestCase> performanceTests();
}
```

### 3. Migration Strategy

#### Phase 1: Parallel Implementation
- Keep existing profile-based tests working
- Implement new builder pattern alongside
- Create new test classes using builder pattern
- Validate parity between approaches

#### Phase 2: Gradual Migration
- Convert simple tests first (MapAgent, Splitter)
- Migrate integration tests
- Update complex workflow tests
- Remove old profile dependencies

#### Phase 3: Cleanup
- Remove deprecated `PromptPipelineTestConfig`
- Clean up unused test profiles
- Update test documentation
- Add test guidelines for new pattern

## Testing Strategy

### Unit Tests for Builder Pattern
- [ ] Test `ChatClientMockBuilder` for each adapter type
- [ ] Verify mock behavior correctness
- [ ] Test error scenario simulations
- [ ] Validate configuration isolation

### Integration Tests
- [ ] End-to-end tests with new mock system
- [ ] Multi-adapter workflow testing
- [ ] Error handling validation
- [ ] Performance impact assessment

### Regression Tests
- [ ] Ensure all existing functionality works
- [ ] Compare test results between old and new approaches
- [ ] Validate test isolation properties
- [ ] Check for memory leaks in test cleanup

## Success Criteria

### Functional Requirements
- [ ] All existing tests pass with new pattern
- [ ] Multiple adapters can be tested simultaneously
- [ ] Dynamic mock configuration works per test method
- [ ] No performance degradation in test execution

### Quality Requirements  
- [ ] Test isolation maintained (no cross-test interference)
- [ ] Clear separation of test data and mock setup
- [ ] Improved test readability and maintainability
- [ ] Comprehensive documentation for new pattern

### Technical Requirements
- [ ] Backward compatibility maintained during migration
- [ ] Spring Boot best practices followed
- [ ] No increase in test execution time
- [ ] Proper cleanup of mock resources

## Risk Mitigation

### Technical Risks
- **Mock Complexity**: Start with simple mocks, gradually add complexity
- **Test Flakiness**: Implement proper mock reset and cleanup
- **Performance Overhead**: Profile test execution, optimize mock creation

### Migration Risks
- **Test Coverage Gaps**: Run comparison tests during migration
- **Breaking Changes**: Use feature flags during transition
- **Team Adoption**: Provide training and documentation

## Timeline

### Week 1: Core Infrastructure
- Implement `ChatClientMockBuilder` and basic configuration
- Create `ChatClientTestConfig`
- Add unit tests for builder pattern

### Week 2: Test Data Enhancement  
- Extend `AdapterTestCase` with mock configuration
- Create `AdapterTestDataProvider`
- Implement basic parameterized tests

### Week 3: Migration & Validation
- Convert existing adapter tests
- Add multi-adapter test scenarios
- Validate parity with current approach

### Week 4: Advanced Features & Cleanup
- Implement error injection and performance tests
- Remove deprecated profile-based configuration
- Finalize documentation and guidelines

## Deliverables

1. **Core Implementation**: Builder pattern classes and test configuration
2. **Test Enhancements**: Extended parameterized tests with dynamic mocks  
3. **Migration Scripts**: Automated test conversion tools
4. **Documentation**: Updated testing guidelines and examples
5. **Cleanup**: Removal of deprecated profile-based test config

## Post-Implementation Benefits

### Improved Test Flexibility
- Dynamic mock configuration per test scenario
- Support for complex multi-adapter workflows
- Easy error scenario simulation

### Better Maintainability
- Clear separation of test data and mock setup
- Reusable mock configuration patterns
- Reduced coupling to Spring profiles

### Enhanced Test Coverage
- Ability to test adapter interactions
- Error handling and resilience testing
- Performance and load testing capabilities

---

**Status**: Planning Phase  
**Next Step**: Begin Phase 1 implementation with core builder infrastructure
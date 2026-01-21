package com.hdekker.ai_workflow.test.pipeline.factory;

import java.util.List;
import java.util.stream.Stream;

import com.hdekker.ai_workflow.test.pipeline.mock.MockConfiguration;
import com.hdekker.ai_workflow.test.pipeline.mock.MockResponseProvider;

/**
 * Provider for test data streams used in parameterized LLM adapter tests.
 * Supplies various test scenarios including single adapters, multi-adapter workflows,
 * error scenarios, and performance tests.
 */
public class AdapterTestDataProvider {

    /**
     * Provides test cases for single adapter testing (Map, Split, Reducer).
     */
    public static Stream<AdapterTestCase> singleAdapterTests() {
        return Stream.of(
            AdapterTestCase.forMapAdapterWithResponses(MockResponseProvider.getMapAgentResponse()),
            AdapterTestCase.forSplitterWorkflow(List.of(MockResponseProvider.getSplitterResponse())),
            AdapterTestCase.forReducerChain(List.of(
                List.of(MockResponseProvider.getReducerInitialResponse()),
                List.of(MockResponseProvider.getReducerAccumulatedResponse())
            ))
        );
    }

    /**
     * Provides test cases for multi-adapter workflow testing.
     * Tests combinations of adapters working together.
     */
    public static Stream<AdapterTestCase> multiAdapterWorkflows() {
        return Stream.of(
            // Map followed by Split
            AdapterTestCase.forMapAdapterWithResponses(
                MockResponseProvider.getMapAgentResponse(),
                MockResponseProvider.getSplitterResponse()
            ),
            // Multiple reducer accumulations
            AdapterTestCase.forReducerChain(List.of(
                List.of(MockResponseProvider.getReducerInitialResponse()),
                List.of(MockResponseProvider.getReducerAccumulatedResponse()),
                List.of(MockResponseProvider.getReducerAccumulatedResponse())
            ))
        );
    }

    /**
     * Provides test cases for error scenario testing.
     * Includes timeout, empty responses, and malformed data.
     */
    public static Stream<AdapterTestCase> errorScenarios() {
        MockConfiguration errorConfig = MockConfiguration.builder()
            .behavior(MockConfiguration.MockBehavior.ERROR)
            .property("errorType", RuntimeException.class)
            .build();

        MockConfiguration timeoutConfig = MockConfiguration.builder()
            .behavior(MockConfiguration.MockBehavior.TIMEOUT)
            .property("timeoutMs", 100L)
            .build();

        MockConfiguration emptyConfig = MockConfiguration.builder()
            .behavior(MockConfiguration.MockBehavior.EMPTY_RESPONSE)
            .build();

        return Stream.of(
            new AdapterTestCase(
                "Map",
                TestConfigurationFactory.createMapAgentDefinition(),
                "",
                1,
                0,
                "Error Scenario Test",
                new String[]{},
                errorConfig
            ),
            new AdapterTestCase(
                "Map",
                TestConfigurationFactory.createMapAgentDefinition(),
                "",
                1,
                0,
                "Timeout Scenario Test",
                new String[]{},
                timeoutConfig
            ),
            new AdapterTestCase(
                "Map",
                TestConfigurationFactory.createMapAgentDefinition(),
                "",
                1,
                0,
                "Empty Response Test",
                new String[]{},
                emptyConfig
            )
        );
    }

    /**
     * Provides test cases for performance and load testing.
     * Includes large response sets and concurrent processing scenarios.
     */
    public static Stream<AdapterTestCase> performanceTests() {
        // Create a large list of responses for performance testing
        List<String> largeResponseSet = List.of(
            MockResponseProvider.getMapAgentResponse(),
            MockResponseProvider.getSplitterResponse(),
            MockResponseProvider.getReducerInitialResponse()
        );

        return Stream.of(
            AdapterTestCase.forMapAdapterWithResponses(
                largeResponseSet.toArray(new String[0])
            ),
            AdapterTestCase.forReducerChain(List.of(largeResponseSet))
        );
    }

    /**
     * Provides all test cases combined for comprehensive testing.
     */
    public static Stream<AdapterTestCase> allTests() {
        return Stream.concat(
            Stream.concat(singleAdapterTests(), multiAdapterWorkflows()),
            Stream.concat(errorScenarios(), performanceTests())
        );
    }
}
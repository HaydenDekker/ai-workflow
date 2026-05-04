package com.hdekker.ai_workflow.test.pipeline.factory;

import java.util.List;

import com.hdekker.ai_workflow.domain.agent.AgentDefinition;
import com.hdekker.ai_workflow.test.pipeline.mock.MockConfiguration;
import com.hdekker.ai_workflow.test.pipeline.mock.MockResponseProvider;

/**
 * Test case data structure for parameterized LLM adapter testing.
 * Contains all necessary information to test a specific adapter type.
 */
public record AdapterTestCase(
    String adapterType,
    AgentDefinition agentDefinition,
    String mockResponse,
    int inputCount,
    int expectedOutputCount,
    String testName,
    String[] expectedFileKeys,
    MockConfiguration mockConfig
) {
    
    /**
     * Get the expected output count for this adapter type.
     * For Map and Reducer adapters, this should equal inputCount.
     * For Split adapters, this depends on the number of splits in mockResponse.
     */
    public int getExpectedOutputCount() {
        return expectedOutputCount;
    }
    
    /**
     * Check if this test case is for a Split adapter.
     */
    public boolean isSplitAdapter() {
        return "Split".equals(adapterType);
    }
    
    /**
     * Check if this test case is for a Reducer adapter.
     */
    public boolean isReducerAdapter() {
        return "Reduction".equals(adapterType);
    }
    
    /**
     * Check if this test case is for a Map adapter.
     */
    public boolean isMapAdapter() {
        return !isSplitAdapter() && !isReducerAdapter();
    }

    /**
     * Factory method for creating Map adapter test cases with responses.
     */
    public static AdapterTestCase forMapAdapterWithResponses(String... responses) {
        MockConfiguration config = MockConfiguration.builder()
            .responses(List.of(responses))
            .build();
        AgentDefinition def = TestConfigurationFactory.createMapAgentDefinition();
        return new AdapterTestCase(
            "Map",
            def,
            responses.length > 0 ? responses[0] : "",
            1,
            1,
            "Map Adapter Test",
            new String[]{},
            config
        );
    }

    /**
     * Factory method for creating Splitter adapter test cases.
     */
    public static AdapterTestCase forSplitterWorkflow(List<String> splitResponses) {
        MockConfiguration config = MockConfiguration.builder()
            .responses(splitResponses)
            .build();
        AgentDefinition def = TestConfigurationFactory.createSplitterAgentDefinition();
        return new AdapterTestCase(
            "Split",
            def,
            splitResponses.isEmpty() ? "" : splitResponses.get(0),
            1,
            splitResponses.size(),
            "Splitter Adapter Test",
            MockResponseProvider.getSplitterKeys(),
            config
        );
    }

    /**
     * Factory method for creating Reducer adapter test cases.
     */
    public static AdapterTestCase forReducerChain(List<List<String>> accumulatedResponses) {
        List<String> allResponses = accumulatedResponses.stream()
            .flatMap(List::stream)
            .toList();
        MockConfiguration config = MockConfiguration.builder()
            .responses(allResponses)
            .build();
        AgentDefinition def = TestConfigurationFactory.createReducerAgentDefinition();
        return new AdapterTestCase(
            "Reduction",
            def,
            allResponses.isEmpty() ? "" : allResponses.get(0),
            accumulatedResponses.size(),
            1,
            "Reducer Adapter Test",
            new String[]{},
            config
        );
    }
}
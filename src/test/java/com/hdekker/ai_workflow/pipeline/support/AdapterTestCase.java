package com.hdekker.ai_workflow.pipeline.support;

import com.hdekker.ai_workflow.pipeline.domain.AgentDefinition;

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
    String[] expectedFileKeys
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
}
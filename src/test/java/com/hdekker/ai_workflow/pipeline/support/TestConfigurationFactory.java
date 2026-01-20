package com.hdekker.ai_workflow.pipeline.support;

import com.hdekker.ai_workflow.pipeline.domain.AgentDefinition;

/**
 * Factory for creating test configurations for different LLM adapter types.
 * Provides AgentDefinition instances and related test data for each adapter type.
 */
public class TestConfigurationFactory {

    /**
     * Creates an AgentDefinition for MapAgentLLMAdapter testing.
     * Map adapter provides 1:1 transformation from input to output.
     */
    public static AgentDefinition createMapAgentDefinition() {
        return new AgentDefinition(
            ".*\\.java",  // Match Java files
            "FUNCTION_ANALYSIS",
            "Analyze the function and provide details.",
            "Process this Java function and return a structured analysis.",
            "Return a JSON object with function name, details, and category.",
            "output/function-analysis/analysis/${name}.md"
        );
    }

    /**
     * Creates an AgentDefinition for SplitterLLMAdapter testing.
     * Split adapter parses responses with --- ItemKey --- tokens.
     */
    public static AgentDefinition createSplitterAgentDefinition() {
        return new AgentDefinition(
            ".*\\.java",  // Match Java files
            "SOLID_NON_COMPLIANCE",
            "Analyze SOLID principle violations.",
            "Analyze this Java code for SOLID principle violations and categorize them.",
            "Split the analysis into different violation categories using --- ItemKey --- format.",
            "output/solid-priority/non-compliance/${name}.md"
        );
    }

    /**
     * Creates an AgentDefinition for ReducerLLMAdapter testing.
     * Reducer adapter maintains state across multiple inputs.
     */
    public static AgentDefinition createReducerAgentDefinition() {
        return new AgentDefinition(
            ".*\\.md",   // Match markdown files
            "FUNCTION_SUMMARY",
            "Summarize function analyses.",
            "Accumulate this new function analysis into the previous response.",
            "List all analyzed functions with their categories and provide a system summary.",
            "output/function-analysis/summary.md"
        );
    }

    /**
     * Creates an AgentDefinition for the default Map adapter (when agentType is null/empty).
     */
    public static AgentDefinition createDefaultMapAgentDefinition() {
        return new AgentDefinition(
            ".*\\.java",
            "DEFAULT_PROCESSING",
            "Process input file.",
            "Process this file and return a structured response.",
            "Return a JSON object with processing results.",
            "output/default/${name}.md"
        );
    }

    /**
     * Creates an AgentDefinition with custom parameters for flexibility.
     */
    public static AgentDefinition createCustomDefinition(
            String fileInputRegex,
            String title,
            String body,
            String prompt,
            String outputStructure,
            String outputFilenameTemplate) {
        return new AgentDefinition(
            fileInputRegex,
            title,
            body,
            prompt,
            outputStructure,
            outputFilenameTemplate
        );
    }
}
package com.hdekker.ai_workflow.test.pipeline.factory;

import com.hdekker.ai_workflow.domain.agent.AgentDefinition;

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
            ".*[/\\\\](?<name>[^.]+)\\.java$",  // Match full URL path and capture Java filename
            "FUNCTION_ANALYSIS",
            "Process this Java function and return a structured analysis.",
            "Map",
            "Return a JSON object with function name, details, and category.",
            "output/function-analysis/analysis/${name}.md",
            "/tmp/test-dir"
        );
    }

    /**
     * Creates an AgentDefinition for SplitterLLMAdapter testing.
     * Split adapter parses responses with --- ItemKey --- tokens.
     */
    public static AgentDefinition createSplitterAgentDefinition() {
        return new AgentDefinition(
            ".*[/\\\\](?<name>[^.]+)\\.java$",  // Match full URL path and capture Java filename
            "SOLID_NON_COMPLIANCE",
            "Analyze this Java code for SOLID principle violations and categorize them.",
            "Split",
            "Split the analysis into different violation categories using --- ItemKey --- format.",
            "output/solid-priority/non-compliance/${name}.md",
            "/tmp/test-dir"
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
            "Accumulate this new function analysis into the previous response.",
            "Reduction",
            "List all analyzed functions with their categories and provide a system summary.",
            "output/function-analysis/summary.md",
            "/tmp/test-dir"
        );
    }

    /**
     * Creates an AgentDefinition for the default Map adapter (when agentType is null/empty).
     */
    public static AgentDefinition createDefaultMapAgentDefinition() {
        return new AgentDefinition(
            ".*[/\\\\](?<name>[^.]+)\\.java$",  // Match full URL path and capture Java filename
            "DEFAULT_PROCESSING",
            "Process this file and return a structured response.",
            null, // null agentType should default to Map
            "Return a JSON object with processing results.",
            "output/default/${name}.md",
            "/tmp/test-dir"
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
        return createCustomDefinition(fileInputRegex, title, body, prompt, outputStructure, outputFilenameTemplate, "/tmp/test-dir");
    }

    /**
     * Creates an AgentDefinition with custom parameters including target directory.
     */
    public static AgentDefinition createCustomDefinition(
            String fileInputRegex,
            String title,
            String body,
            String prompt,
            String outputStructure,
            String outputFilenameTemplate,
            String targetDirectory) {
        return new AgentDefinition(
            fileInputRegex,
            title,
            body,
            prompt,
            outputStructure,
            outputFilenameTemplate,
            targetDirectory
        );
    }
}
package com.hdekker.ai_workflow.prompt;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;


import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class SystemPromptConfigurationTest {
    
    @TempDir
    Path tempDir;
    
    @Test
    void testDeprecatedChainPropertyDetection() throws Exception {
        String yamlWithChain = """
            chain:
              - fileInputRegex: ".*"
                title: TEST
                body: test.md
                agentType: Map
                outputStructure: output.md
                outputFilenameTemplate: output/test.md
            """;
        
        Path yamlFile = tempDir.resolve("deprecated-chain.yml");
        Files.writeString(yamlFile, yamlWithChain);
        
        assertThrows(IllegalArgumentException.class, () -> {
            SystemPromptConfiguration.AgentWorkflowYAMLConfigReader.readYamlFile(yamlFile);
        }, "Deprecated YAML format: use 'agents:' instead of 'chain:'");
    }
    
    @Test
    void testValidAgentsProperty() throws Exception {
        String yamlWithAgents = """
            agents:
              - fileInputRegex: ".*"
                title: TEST
                body: test.md
                agentType: Map
                outputStructure: output.md
                outputFilenameTemplate: output/test.md
            """;
        
        Path yamlFile = tempDir.resolve("valid-agents.yml");
        Files.writeString(yamlFile, yamlWithAgents);
        
        assertDoesNotThrow(() -> {
            SystemPromptConfiguration.AgentWorkflowYAMLConfigReader.readYamlFile(yamlFile);
        });
    }
}

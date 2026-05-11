package com.hdekker.ai_workflow.test.harness.filesystem;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;


import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.hdekker.ai_workflow.domain.agent.AgentDefinition;
import com.hdekker.ai_workflow.domain.agent.AgentType;
import com.hdekker.ai_workflow.test.harness.factory.TestConfigurationFactory;

class YamlTestUtilsTest {

    @TempDir
    Path tempDir;

    @Test
    void testAgentDefinitionToYaml() {
        AgentDefinition definition = TestConfigurationFactory.createMapAgentDefinition();
        
        String yaml = YamlTestUtils.agentDefinitionToYaml(definition);
        
        assertNotNull(yaml);
        assertFalse(yaml.isEmpty());
        assertTrue(yaml.contains("fileInputRegex"));
        assertTrue(yaml.contains("title"));
        assertTrue(yaml.contains("body"));
        assertTrue(yaml.contains("agentType"));
        assertTrue(yaml.contains("outputStructure"));
        assertTrue(yaml.contains("outputFilenameTemplate"));
    }

    @Test
    void testYamlToAgentDefinition() {
        AgentDefinition originalDefinition = TestConfigurationFactory.createMapAgentDefinition();
        String yaml = YamlTestUtils.agentDefinitionToYaml(originalDefinition);
        
        AgentDefinition parsedDefinition = YamlTestUtils.yamlToAgentDefinition(yaml);
        
        assertNotNull(parsedDefinition);
        assertEquals(originalDefinition.fileInputRegex(), parsedDefinition.fileInputRegex());
        assertEquals(originalDefinition.title(), parsedDefinition.title());
        assertEquals(originalDefinition.body(), parsedDefinition.body());
        assertEquals(originalDefinition.agentType(), parsedDefinition.agentType());
        assertEquals(originalDefinition.outputStructure(), parsedDefinition.outputStructure());
        assertEquals(originalDefinition.outputFilenameTemplate(), parsedDefinition.outputFilenameTemplate());
    }

    @Test
    void testWriteYamlFile() throws IOException {
        AgentDefinition definition = TestConfigurationFactory.createSplitterAgentDefinition();
        Path yamlFile = tempDir.resolve("test-definition.yaml");
        
        YamlTestUtils.writeYamlFile(yamlFile, definition);
        
        assertTrue(Files.exists(yamlFile));
        String fileContent = Files.readString(yamlFile);
        assertFalse(fileContent.isEmpty());
        assertTrue(fileContent.contains("SOLID_NON_COMPLIANCE"));
    }

    @Test
    void testReadYamlFile() throws IOException {
        AgentDefinition originalDefinition = TestConfigurationFactory.createReducerAgentDefinition();
        Path yamlFile = tempDir.resolve("test-definition.yaml");
        
        // Write the file first
        YamlTestUtils.writeYamlFile(yamlFile, originalDefinition);
        
        // Read it back
        AgentDefinition readDefinition = YamlTestUtils.readYamlFile(yamlFile);
        
        assertNotNull(readDefinition);
        YamlTestUtils.areEqual(originalDefinition, readDefinition);
    }

    @Test
    void testIsValidYaml() {
        AgentDefinition definition = TestConfigurationFactory.createMapAgentDefinition();
        String validYaml = YamlTestUtils.agentDefinitionToYaml(definition);
        String invalidYaml = "invalid: yaml: content: [unclosed";
        
        assertTrue(YamlTestUtils.isValidYaml(validYaml));
        assertFalse(YamlTestUtils.isValidYaml(invalidYaml));
        assertFalse(YamlTestUtils.isValidYaml(null));
        assertFalse(YamlTestUtils.isValidYaml(""));
    }

    @Test
    void testIsValidYamlFile() throws IOException {
        AgentDefinition definition = TestConfigurationFactory.createMapAgentDefinition();
        Path validFile = tempDir.resolve("valid.yaml");
        Path invalidFile = tempDir.resolve("invalid.yaml");
        Path nonExistentFile = tempDir.resolve("nonexistent.yaml");
        
        // Create valid YAML file
        YamlTestUtils.writeYamlFile(validFile, definition);
        
        // Create invalid YAML file
        Files.writeString(invalidFile, "invalid: yaml: content: [unclosed");
        
        assertTrue(YamlTestUtils.isValidYamlFile(validFile));
        assertFalse(YamlTestUtils.isValidYamlFile(invalidFile));
        assertFalse(YamlTestUtils.isValidYamlFile(nonExistentFile));
        
        // Test with directory instead of file
        Path directory = tempDir.resolve("directory");
        Files.createDirectories(directory);
        assertFalse(YamlTestUtils.isValidYamlFile(directory));
    }

    @Test
    void testAreEqual() {
        AgentDefinition definition1 = TestConfigurationFactory.createMapAgentDefinition();
        AgentDefinition definition2 = TestConfigurationFactory.createMapAgentDefinition();
        AgentDefinition definition3 = TestConfigurationFactory.createSplitterAgentDefinition();
        
        assertTrue(YamlTestUtils.areEqual(definition1, definition2));
        assertFalse(YamlTestUtils.areEqual(definition1, definition3));
        
        // Test null cases
        assertTrue(YamlTestUtils.areEqual(null, null));
        assertFalse(YamlTestUtils.areEqual(definition1, null));
        assertFalse(YamlTestUtils.areEqual(null, definition1));
        
        // Test with custom definition having null agentType
        AgentDefinition defWithNullType = TestConfigurationFactory.createDefaultMapAgentDefinition();
        AgentDefinition defWithEmptyType = new AgentDefinition(
            ".*\\.java", "TITLE", "BODY", AgentType.MAP, "STRUCTURE", "TEMPLATE", "/tmp/test"
        );
        
        assertFalse(YamlTestUtils.areEqual(defWithNullType, defWithEmptyType));
    }

    @Test
    void testRoundTripTest() {
        AgentDefinition mapDefinition = TestConfigurationFactory.createMapAgentDefinition();
        AgentDefinition splitDefinition = TestConfigurationFactory.createSplitterAgentDefinition();
        AgentDefinition reducerDefinition = TestConfigurationFactory.createReducerAgentDefinition();
        AgentDefinition defaultDefinition = TestConfigurationFactory.createDefaultMapAgentDefinition();
        
        assertTrue(YamlTestUtils.roundTripTest(mapDefinition));
        assertTrue(YamlTestUtils.roundTripTest(splitDefinition));
        assertTrue(YamlTestUtils.roundTripTest(reducerDefinition));
        assertTrue(YamlTestUtils.roundTripTest(defaultDefinition));
    }

    @Test
    void testRoundTripTestWithCustomDefinition() {
        AgentDefinition customDefinition = TestConfigurationFactory.createCustomDefinition(
            ".*\\.md", "Custom Title", "Custom Body", "Split", 
            "Custom Structure", "custom/${filename}.output"
        );
        
        assertTrue(YamlTestUtils.roundTripTest(customDefinition));
    }

    @Test
    void testAgentDefinitionToYamlWithNullableFields() {
        // Only outputStructure, outputFilenameTemplate, targetDirectory are nullable
        AgentDefinition definition = new AgentDefinition(
            ".*\\.java", "Title", "body", AgentType.MAP, null, null, null
        );
        
        String yaml = YamlTestUtils.agentDefinitionToYaml(definition);
        
        assertNotNull(yaml);
        // Should handle null nullable fields gracefully
        assertDoesNotThrow(() -> YamlTestUtils.agentDefinitionToYaml(definition));
    }

    @Test
    void testYamlToAgentDefinitionWithInvalidInput() {
        String malformedYaml = "not: a: valid: yaml: document";
        
        assertThrows(RuntimeException.class, 
            () -> YamlTestUtils.yamlToAgentDefinition(malformedYaml));
    }

    @Test
    void testReadYamlFileWithNonExistentFile() {
        Path nonExistentFile = tempDir.resolve("nonexistent.yaml");
        
        assertThrows(IOException.class, 
            () -> YamlTestUtils.readYamlFile(nonExistentFile));
    }

    @Test
    void testWriteYamlFileWithNullDefinition() throws IOException {
        Path yamlFile = tempDir.resolve("test.yaml");
        
        assertThrows(RuntimeException.class, 
            () -> YamlTestUtils.writeYamlFile(yamlFile, null));
    }

    @Test
    void testComplexAgentDefinitionSerialization() {
        // Test with complex strings that might contain special characters
        AgentDefinition complexDefinition = new AgentDefinition(
            ".*\\.(java|kt|scala)",  // Complex regex
            "Complex \"Title\" with 'quotes' and \n newlines",
            "Multi-line\nbody\nwith\nspecial\ncharacters: {}[]",
            AgentType.SPLIT,
            "JSON output with \"quotes\": {\"key\": \"value\", \"array\": [1,2,3]}",
            "output/${filename}-${timestamp}.md",
            "/tmp/test-dir"
        );
        
        assertTrue(YamlTestUtils.roundTripTest(complexDefinition));
    }

    @Test
    void testAllTestConfigurationFactoryDefinitions() {
        // Test round-trip for all definitions from TestConfigurationFactory
        AgentDefinition[] definitions = {
            TestConfigurationFactory.createMapAgentDefinition(),
            TestConfigurationFactory.createSplitterAgentDefinition(),
            TestConfigurationFactory.createReducerAgentDefinition(),
            TestConfigurationFactory.createDefaultMapAgentDefinition()
        };
        
        for (AgentDefinition definition : definitions) {
            assertTrue(YamlTestUtils.roundTripTest(definition), 
                "Round-trip test failed for definition: " + definition.title());
        }
    }

    @Test
    void testYamlFormatConsistency() {
        AgentDefinition definition = TestConfigurationFactory.createMapAgentDefinition();
        
        String yaml1 = YamlTestUtils.agentDefinitionToYaml(definition);
        String yaml2 = YamlTestUtils.agentDefinitionToYaml(definition);
        
        // YAML output should be consistent for the same input
        assertEquals(yaml1, yaml2);
    }
}
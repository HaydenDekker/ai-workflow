package com.hdekker.ai_workflow.test.pipeline.filesystem;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.hdekker.ai_workflow.domain.agent.AgentDefinition;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Utilities to convert AgentDefinition objects to/from YAML for test scenarios.
 * Provides serialization and deserialization methods compatible with the system's YAML parser.
 */
public class YamlTestUtils {

    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());

    /**
     * Convert AgentDefinition object to YAML format string.
     * 
     * @param definition AgentDefinition to serialize
     * @return YAML string representation
     * @throws RuntimeException if serialization fails
     */
    public static String agentDefinitionToYaml(AgentDefinition definition) {
        if (definition == null) {
            throw new RuntimeException("AgentDefinition cannot be null");
        }
        try {
            return YAML_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(definition);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize AgentDefinition to YAML", e);
        }
    }

    /**
     * Parse YAML string back to AgentDefinition object.
     * 
     * @param yaml YAML string to parse
     * @return AgentDefinition object
     * @throws RuntimeException if parsing fails
     */
    public static AgentDefinition yamlToAgentDefinition(String yaml) {
        try {
            return YAML_MAPPER.readValue(yaml, AgentDefinition.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse YAML to AgentDefinition", e);
        }
    }

    /**
     * Write AgentDefinition object to YAML file.
     * 
     * @param filePath Path where the YAML file should be created
     * @param definition AgentDefinition to serialize and write
     * @throws IOException if file writing fails
     */
    public static void writeYamlFile(Path filePath, AgentDefinition definition) throws IOException {
        String yamlContent = agentDefinitionToYaml(definition);
        Files.writeString(filePath, yamlContent);
    }

    /**
     * Read AgentDefinition object from YAML file.
     * 
     * @param filePath Path to the YAML file
     * @return AgentDefinition object
     * @throws IOException if file reading fails
     */
    public static AgentDefinition readYamlFile(Path filePath) throws IOException {
        String yamlContent = Files.readString(filePath);
        return yamlToAgentDefinition(yamlContent);
    }

    /**
     * Validate that YAML structure matches system expectations.
     * This method attempts to parse the YAML and returns true if successful.
     * 
     * @param yaml YAML string to validate
     * @return true if valid, false otherwise
     */
    public static boolean isValidYaml(String yaml) {
        try {
            yamlToAgentDefinition(yaml);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Validate that a YAML file contains a valid AgentDefinition.
     * 
     * @param filePath Path to the YAML file
     * @return true if valid, false otherwise
     */
    public static boolean isValidYamlFile(Path filePath) {
        try {
            if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
                return false;
            }
            readYamlFile(filePath);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Compare two AgentDefinition objects for equality.
     * 
     * @param def1 First AgentDefinition
     * @param def2 Second AgentDefinition
     * @return true if equal, false otherwise
     */
    public static boolean areEqual(AgentDefinition def1, AgentDefinition def2) {
        if (def1 == null && def2 == null) return true;
        if (def1 == null || def2 == null) return false;
        
        return java.util.Objects.equals(def1.fileInputRegex(), def2.fileInputRegex()) &&
               java.util.Objects.equals(def1.title(), def2.title()) &&
               java.util.Objects.equals(def1.body(), def2.body()) &&
               java.util.Objects.equals(def1.agentType(), def2.agentType()) &&
               java.util.Objects.equals(def1.outputStructure(), def2.outputStructure()) &&
               java.util.Objects.equals(def1.outputFilenameTemplate(), def2.outputFilenameTemplate());
    }

    /**
     * Perform round-trip conversion test: serialize then deserialize AgentDefinition.
     * 
     * @param definition AgentDefinition to test
     * @return true if round-trip conversion produces equal object, false otherwise
     */
    public static boolean roundTripTest(AgentDefinition definition) {
        try {
            String yaml = agentDefinitionToYaml(definition);
            AgentDefinition deserialized = yamlToAgentDefinition(yaml);
            return areEqual(definition, deserialized);
        } catch (Exception e) {
            return false;
        }
    }
}
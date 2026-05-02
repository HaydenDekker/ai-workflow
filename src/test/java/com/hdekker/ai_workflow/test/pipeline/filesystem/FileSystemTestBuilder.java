package com.hdekker.ai_workflow.test.pipeline.filesystem;

import com.hdekker.ai_workflow.domain.agent.AgentDefinition;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Utilities to create AgentDefinition files and directory structures for testing.
 * Provides methods to setup complete test environments with file system isolation.
 */
public class FileSystemTestBuilder {

    /**
     * Creates AgentDefinition YAML files from test configurations.
     * 
     * @param tempDir Temporary directory for file creation
     * @param definitions List of AgentDefinition instances to convert to YAML
     * @return List of created file paths
     * @throws IOException if file creation fails
     */
    public static List<Path> createAgentDefinitionFiles(Path tempDir, List<AgentDefinition> definitions) throws IOException {
        List<Path> createdFiles = new ArrayList<>();
        
        for (int i = 0; i < definitions.size(); i++) {
            AgentDefinition definition = definitions.get(i);
            String fileName = "agent-definition-" + i + ".yaml";
            Path filePath = tempDir.resolve(fileName);
            
            YamlTestUtils.writeYamlFile(filePath, definition);
            createdFiles.add(filePath);
        }
        
        return createdFiles;
    }

    /**
     * Setup complete directory structure for testing.
     * 
     * @param root Root directory for the test structure
     * @return TestDirectoryStructure with all paths created
     * @throws IOException if directory creation fails
     */
    public static TestDirectoryStructure setupDirectoryStructure(Path root) throws IOException {
        Path promptConfigDir = root.resolve("prompt-config");
        Path inputDir = root.resolve("input");
        Path outputDir = root.resolve("output");
        
        // Create directories
        Files.createDirectories(promptConfigDir);
        Files.createDirectories(inputDir);
        Files.createDirectories(outputDir);
        
        return new TestDirectoryStructure(root, promptConfigDir, inputDir, outputDir);
    }

    /**
     * Create test input files for processing.
     * 
     * @param inputDir Directory where input files should be created
     * @param fileContents Variable array of file contents to create
     * @return List of created file paths
     * @throws IOException if file creation fails
     */
    public static List<Path> createTestInputFiles(Path inputDir, String... fileContents) throws IOException {
        List<Path> createdFiles = new ArrayList<>();
        
        for (int i = 0; i < fileContents.length; i++) {
            String fileName = "test-file-" + i + ".java";
            Path filePath = inputDir.resolve(fileName);
            
            Files.writeString(filePath, fileContents[i]);
            createdFiles.add(filePath);
        }
        
        return createdFiles;
    }

    /**
     * Verify output files match expectations.
     * 
     * @param outputDir Directory containing output files
     * @param expectations Variable array of expected outputs
     * @throws IOException if file reading fails or verification fails
     */
    public static void verifyOutputFiles(Path outputDir, ExpectedOutput... expectations) throws IOException {
        for (ExpectedOutput expectation : expectations) {
            Path expectedFile = outputDir.resolve(expectation.fileName());
            
            if (!Files.exists(expectedFile)) {
                throw new AssertionError("Expected output file does not exist: " + expectedFile);
            }
            
            String actualContent = Files.readString(expectedFile);
            if (!expectation.content().equals(actualContent)) {
                throw new AssertionError("Content mismatch in file: " + expectation.fileName() +
                    "\nExpected: " + expectation.content() +
                    "\nActual: " + actualContent);
            }
        }
    }

    /**
     * Create a single test input file with specified name.
     * 
     * @param inputDir Directory where the file should be created
     * @param fileName Name of the file to create
     * @param content Content of the file
     * @return Path to the created file
     * @throws IOException if file creation fails
     */
    public static Path createTestInputFile(Path inputDir, String fileName, String content) throws IOException {
        Path filePath = inputDir.resolve(fileName);
        Files.writeString(filePath, content);
        return filePath;
    }

    /**
     * Verify that a directory contains the expected number of files.
     * 
     * @param directory Directory to check
     * @param expectedCount Expected number of files
     * @throws IOException if directory reading fails
     */
    public static void verifyFileCount(Path directory, int expectedCount) throws IOException {
        try (Stream<Path> files = Files.list(directory)) {
            long actualCount = files.count();
            if (actualCount != expectedCount) {
                throw new AssertionError("Expected " + expectedCount + " files in " + directory +
                    ", but found " + actualCount);
            }
        }
    }

    /**
     * Check if a file exists in the specified directory.
     * 
     * @param directory Directory to check
     * @param fileName Name of the file to look for
     * @return true if file exists, false otherwise
     */
    public static boolean fileExists(Path directory, String fileName) {
        Path filePath = directory.resolve(fileName);
        return Files.exists(filePath);
    }

    /**
     * Read content of a file in the specified directory.
     * 
     * @param directory Directory containing the file
     * @param fileName Name of the file to read
     * @return Content of the file
     * @throws IOException if file reading fails
     */
    public static String readFileContent(Path directory, String fileName) throws IOException {
        Path filePath = directory.resolve(fileName);
        return Files.readString(filePath);
    }

    /**
     * Record representing a complete test directory structure.
     */
    public record TestDirectoryStructure(
        Path root,
        Path promptConfigDir,
        Path inputDir, 
        Path outputDir
    ) {}

    /**
     * Record representing expected output for verification.
     */
    public record ExpectedOutput(
        String fileName,
        String content
    ) {}
}
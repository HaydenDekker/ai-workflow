package com.hdekker.ai_workflow.test.harness.filesystem;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;


import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.hdekker.ai_workflow.domain.agent.AgentDefinition;
import com.hdekker.ai_workflow.test.harness.factory.TestConfigurationFactory;

class FileSystemTestBuilderTest {

    @TempDir
    Path tempDir;

    @Test
    void testCreateAgentDefinitionFiles() throws IOException {
        // Create test AgentDefinitions using the factory
        AgentDefinition mapDef = TestConfigurationFactory.createMapAgentDefinition();
        AgentDefinition splitDef = TestConfigurationFactory.createSplitterAgentDefinition();
        List<AgentDefinition> definitions = List.of(mapDef, splitDef);

        // Create YAML files
        List<Path> createdFiles = FileSystemTestBuilder.createAgentDefinitionFiles(tempDir, definitions);

        // Verify files were created
        assertEquals(2, createdFiles.size());
        assertTrue(Files.exists(tempDir.resolve("agent-definition-0.yaml")));
        assertTrue(Files.exists(tempDir.resolve("agent-definition-1.yaml")));

        // Verify file contents are valid YAML and can be read back
        AgentDefinition readMapDef = YamlTestUtils.readYamlFile(createdFiles.get(0));
        AgentDefinition readSplitDef = YamlTestUtils.readYamlFile(createdFiles.get(1));

        YamlTestUtils.areEqual(mapDef, readMapDef);
        YamlTestUtils.areEqual(splitDef, readSplitDef);
    }

    @Test
    void testSetupDirectoryStructure() throws IOException {
        // Setup directory structure
        FileSystemTestBuilder.TestDirectoryStructure structure = 
            FileSystemTestBuilder.setupDirectoryStructure(tempDir);

        // Verify all directories were created
        assertTrue(Files.exists(structure.root()));
        assertTrue(Files.exists(structure.promptConfigDir()));
        assertTrue(Files.exists(structure.inputDir()));
        assertTrue(Files.exists(structure.outputDir()));
        assertTrue(Files.isDirectory(structure.promptConfigDir()));
        assertTrue(Files.isDirectory(structure.inputDir()));
        assertTrue(Files.isDirectory(structure.outputDir()));

        // Verify directory names
        assertEquals("prompt-config", structure.promptConfigDir().getFileName().toString());
        assertEquals("input", structure.inputDir().getFileName().toString());
        assertEquals("output", structure.outputDir().getFileName().toString());
    }

    @Test
    void testCreateTestInputFiles() throws IOException {
        Path inputDir = tempDir.resolve("input");
        Files.createDirectories(inputDir);

        String[] fileContents = {
            "public class TestClass1 { }",
            "public class TestClass2 { }",
            "public class TestClass3 { }"
        };

        // Create test input files
        List<Path> createdFiles = FileSystemTestBuilder.createTestInputFiles(inputDir, fileContents);

        // Verify files were created
        assertEquals(3, createdFiles.size());
        assertTrue(Files.exists(inputDir.resolve("test-file-0.java")));
        assertTrue(Files.exists(inputDir.resolve("test-file-1.java")));
        assertTrue(Files.exists(inputDir.resolve("test-file-2.java")));

        // Verify file contents
        for (int i = 0; i < fileContents.length; i++) {
            String actualContent = Files.readString(createdFiles.get(i));
            assertEquals(fileContents[i], actualContent);
        }
    }

    @Test
    void testCreateTestInputFile() throws IOException {
        Path inputDir = tempDir.resolve("input");
        Files.createDirectories(inputDir);

        String fileName = "CustomTestFile.java";
        String content = "public class CustomTestFile { private int value; }";

        // Create single test input file
        Path createdFile = FileSystemTestBuilder.createTestInputFile(inputDir, fileName, content);

        // Verify file was created with correct name and content
        assertEquals(fileName, createdFile.getFileName().toString());
        assertTrue(Files.exists(createdFile));
        String actualContent = Files.readString(createdFile);
        assertEquals(content, actualContent);
    }

    @Test
    void testVerifyOutputFiles() throws IOException {
        Path outputDir = tempDir.resolve("output");
        Files.createDirectories(outputDir);

        // Create output files
        Path file1 = outputDir.resolve("output1.md");
        Path file2 = outputDir.resolve("output2.md");
        Files.writeString(file1, "Output content 1");
        Files.writeString(file2, "Output content 2");

        // Create expected outputs
        FileSystemTestBuilder.ExpectedOutput[] expectations = {
            new FileSystemTestBuilder.ExpectedOutput("output1.md", "Output content 1"),
            new FileSystemTestBuilder.ExpectedOutput("output2.md", "Output content 2")
        };

        // Verify should pass without exception
        assertDoesNotThrow(() -> FileSystemTestBuilder.verifyOutputFiles(outputDir, expectations));
    }

    @Test
    void testVerifyOutputFiles_WhenFileMissing() throws IOException {
        Path outputDir = tempDir.resolve("output");
        Files.createDirectories(outputDir);

        FileSystemTestBuilder.ExpectedOutput[] expectations = {
            new FileSystemTestBuilder.ExpectedOutput("missing.md", "Some content")
        };

        // Verify should throw AssertionError for missing file
        AssertionError exception = assertThrows(AssertionError.class, 
            () -> FileSystemTestBuilder.verifyOutputFiles(outputDir, expectations));
        assertTrue(exception.getMessage().contains("Expected output file does not exist"));
    }

    @Test
    void testVerifyOutputFiles_WhenContentMismatch() throws IOException {
        Path outputDir = tempDir.resolve("output");
        Files.createDirectories(outputDir);

        // Create output file with different content
        Path file1 = outputDir.resolve("output1.md");
        Files.writeString(file1, "Actual content");

        FileSystemTestBuilder.ExpectedOutput[] expectations = {
            new FileSystemTestBuilder.ExpectedOutput("output1.md", "Expected content")
        };

        // Verify should throw AssertionError for content mismatch
        AssertionError exception = assertThrows(AssertionError.class, 
            () -> FileSystemTestBuilder.verifyOutputFiles(outputDir, expectations));
        assertTrue(exception.getMessage().contains("Content mismatch"));
    }

    @Test
    void testVerifyFileCount() throws IOException {
        Path testDir = tempDir.resolve("test");
        Files.createDirectories(testDir);

        // Create some files
        Files.writeString(testDir.resolve("file1.txt"), "content1");
        Files.writeString(testDir.resolve("file2.txt"), "content2");
        Files.writeString(testDir.resolve("file3.txt"), "content3");

        // Verify correct count
        assertDoesNotThrow(() -> FileSystemTestBuilder.verifyFileCount(testDir, 3));
        
        // Verify incorrect count throws exception
        AssertionError exception = assertThrows(AssertionError.class, 
            () -> FileSystemTestBuilder.verifyFileCount(testDir, 2));
        assertTrue(exception.getMessage().contains("Expected 2 files"));
    }

    @Test
    void testFileExists() throws IOException {
        Path testDir = tempDir.resolve("test");
        Files.createDirectories(testDir);

        // Create a file
        Files.writeString(testDir.resolve("existing.txt"), "content");

        // Test existing file
        assertTrue(FileSystemTestBuilder.fileExists(testDir, "existing.txt"));
        
        // Test non-existing file
        assertFalse(FileSystemTestBuilder.fileExists(testDir, "nonexisting.txt"));
    }

    @Test
    void testReadFileContent() throws IOException {
        Path testDir = tempDir.resolve("test");
        Files.createDirectories(testDir);

        String expectedContent = "Test file content for reading";
        Files.writeString(testDir.resolve("test.txt"), expectedContent);

        // Read file content
        String actualContent = FileSystemTestBuilder.readFileContent(testDir, "test.txt");
        assertEquals(expectedContent, actualContent);
    }

    @Test
    void testReadFileContent_FileNotFound() throws IOException {
        Path testDir = tempDir.resolve("test");
        Files.createDirectories(testDir);

        // Should throw IOException for non-existent file
        assertThrows(IOException.class, 
            () -> FileSystemTestBuilder.readFileContent(testDir, "nonexistent.txt"));
    }

    @Test
    void testTestDirectoryStructureRecord() {
        FileSystemTestBuilder.TestDirectoryStructure structure = 
            new FileSystemTestBuilder.TestDirectoryStructure(tempDir, tempDir.resolve("a"), 
                                                          tempDir.resolve("b"), tempDir.resolve("c"));

        assertEquals(tempDir, structure.root());
        assertEquals(tempDir.resolve("a"), structure.promptConfigDir());
        assertEquals(tempDir.resolve("b"), structure.inputDir());
        assertEquals(tempDir.resolve("c"), structure.outputDir());
    }

    @Test
    void testExpectedOutputRecord() {
        FileSystemTestBuilder.ExpectedOutput output = 
            new FileSystemTestBuilder.ExpectedOutput("test.md", "test content");

        assertEquals("test.md", output.fileName());
        assertEquals("test content", output.content());
    }
}
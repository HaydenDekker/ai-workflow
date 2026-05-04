package com.hdekker.ai_workflow.app.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;


import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hdekker.ai_workflow.database.filemetadata.FileMetadataDatabase;
import com.hdekker.ai_workflow.domain.agent.AgentDefinition;
import com.hdekker.ai_workflow.domain.file.FileMetadata;
import com.hdekker.ai_workflow.domain.prompt.PromptResponse;
import com.hdekker.ai_workflow.test.pipeline.mock.ChatClientMockBuilder;
import com.hdekker.ai_workflow.usecases.Scanner;
import com.hdekker.ai_workflow.usecases.ScannerObserverUseCase;

import org.mockito.Mockito;
import org.springframework.ai.chat.client.ChatClient;
import reactor.core.publisher.Flux;

/**
 * Pipeline integration test: real scanner flux -> AgentConfigurator pipeline -> LLM mock -> output file.
 *
 * Tests that a file placed in a watched directory flows through:
 * 1. FileSystemScannerAdapter (real file system watcher)
 * 2. AgentConfigurator (agent pipeline with LLM adapter)
 * 3. ChatClient mock (captured LLM call)
 * 4. Persister (output file written)
 *
 * This is a non-@SpringBootTest integration test - it uses real scanner + pipeline
 * components but mocks the LLM and database. Each test method is fully self-contained
 * with its own scanner, DB, and temp directory to avoid cross-test interference.
 */
public class AgentPipelineTest {

    private static final Logger log = LoggerFactory.getLogger(AgentPipelineTest.class);

    /**
     * Creates a "smart" mock DB that actually stores and retrieves file metadata.
     * This allows FileComparator to properly deduplicate files across poll cycles.
     */
    private FileMetadataDatabase createSmartDb() {
        FileMetadataDatabase db = Mockito.mock(FileMetadataDatabase.class);
        ConcurrentHashMap<String, FileMetadata> store = new ConcurrentHashMap<>();

        Mockito.doAnswer(inv -> {
            FileMetadata fm = inv.getArgument(0);
            store.put(fm.url(), fm);
            return null;
        }).when(db).save(Mockito.any());

        Mockito.when(db.findById(Mockito.anyString()))
                .thenAnswer(inv -> Optional.ofNullable(store.get(inv.getArgument(0))));

        return db;
    }

    /**
     * Sets up a complete scanner + pipeline and returns all the pieces.
     */
    private SetupResult setupPipeline(Path inputDir, Path outputDir) throws Exception {
        FileMetadataDatabase db = createSmartDb();
        ScannerObserverUseCase observer = new ScannerObserverUseCase(path -> 0L);
        Scanner scanner = new Scanner("test-agent",
                inputDir.toString(),
                Duration.ofMillis(500),
                Duration.ZERO,
                db,
                observer);

        CopyOnWriteArrayList<String> prompts = new CopyOnWriteArrayList<>();
        CopyOnWriteArrayList<PromptResponse> responses = new CopyOnWriteArrayList<>();

        String mockResponse = "## Analysis\n\nDocument processed successfully.\n\nContent: test content";
        ChatClient chatClient = ChatClientMockBuilder.createMock(mockResponse);

        ChatClient.ChatClientRequestSpec requestSpec = Mockito.mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.StreamResponseSpec streamSpec = Mockito.mock(ChatClient.StreamResponseSpec.class);
        Mockito.when(chatClient.prompt(Mockito.anyString())).thenAnswer(inv -> {
            prompts.add(inv.getArgument(0, String.class));
            return requestSpec;
        });
        Mockito.when(requestSpec.stream()).thenReturn(streamSpec);
        Mockito.when(streamSpec.content()).thenReturn(Flux.just(mockResponse));

        CountDownLatch latch = new CountDownLatch(1);

        java.util.function.Consumer<PromptResponse> persister = response -> {
            responses.add(response);
            try {
                Path outputFile = outputDir.resolve(response.createOutputFileName());
                Files.createDirectories(outputFile.getParent());
                Files.writeString(outputFile, response.response());
                log.info("Output file written: {}", outputFile);
            } catch (IOException e) {
                log.error("Failed to write output file", e);
            }
            latch.countDown();
        };

        AgentDefinition agentDef = new AgentDefinition(
                "(?:.*/)?(?<name>[^/]+\\.md)",
                "PIPELINE-INTEGRATION-AGENT",
                "Map",
                "Analyze the provided document and provide a structured summary.",
                "Provide a concise analysis with key points.",
                "analysis-${name}",
                inputDir.toString());

        Flux<PromptResponse> pipeline = new AgentConfigurator(scanner.flux(), chatClient, persister)
                .configure(agentDef);

        pipeline.subscribe(
                response -> log.info("Pipeline emitted response: {}", response.fileName()),
                error -> log.error("Pipeline error", error)
        );

        Thread.sleep(1000); // Let scanner start polling
        return new SetupResult(scanner, db, prompts, responses, latch, outputDir);
    }

    /**
     * Helper record to return all setup pieces from setupPipeline.
     */
    private record SetupResult(
            Scanner scanner,
            FileMetadataDatabase db,
            CopyOnWriteArrayList<String> prompts,
            CopyOnWriteArrayList<PromptResponse> responses,
            CountDownLatch latch,
            Path outputDir
    ) {}

    @AfterEach
    void tearDown() {
        // No shared state to clean up - each test is fully isolated
    }

    /**
     * Main integration test: file placed in watched directory flows through scanner -> pipeline -> LLM -> output.
     *
     * This verifies the full pipeline wiring:
     * 1. Scanner watches the input directory
     * 2. File creation triggers the scanner flux
     * 3. AgentConfigurator processes the file through the LLM adapter
     * 4. ChatClient mock is called (captured)
     * 5. Persister writes output file (captured)
     */
    @Test
    void givenAgentPipeline_WhenFilePlacedInWatchedDir_ThenLLMIsCalledAndOutputCreated() throws Exception {
        log.info("Starting pipeline integration test: file -> scanner -> agent -> LLM -> output");

        Path tempDir = Files.createTempDirectory("agent-pipeline-main-");
        Path inputDir = Files.createDirectory(tempDir.resolve("input"));
        Path outputDir = Files.createDirectory(tempDir.resolve("output"));

        SetupResult setup = setupPipeline(inputDir, outputDir);

        try {
            // Place a test file in the watched input directory
            String testFileName = "test-document.md";
            String testContent = "# Test Document\n\nThis is test content for the pipeline integration test.";
            Files.writeString(inputDir.resolve(testFileName), testContent);

            log.info("Placed test file: {}", inputDir.resolve(testFileName));

            // Wait for the pipeline to process the file (LLM call + output)
            boolean processed = setup.latch.await(15, TimeUnit.SECONDS);
            assertThat(processed)
                    .withFailMessage("Pipeline did not process the file within 15 seconds. "
                            + "Captured {} LLM calls: {}", setup.prompts.size(), setup.prompts)
                    .isTrue();

            // Verify the LLM mock was called
            assertThat(setup.prompts)
                    .withFailMessage("LLM mock was not called - the pipeline did not reach the LLM adapter.")
                    .isNotEmpty();

            // Verify the response was captured
            assertThat(setup.responses)
                    .withFailMessage("No response was captured - the pipeline did not complete.")
                    .isNotEmpty();

            // Verify the output file was created (with retry for FS sync)
            // Add a small delay to ensure the persister has finished writing
            Thread.sleep(500);
            Path outputFile = outputDir.resolve("analysis-test-document.md");
            
            // Debug: check what's in the output directory
            java.io.File[] files = outputDir.toFile().listFiles();
            String fileNames = files != null ? java.util.Arrays.stream(files).map(f -> f.getName()).toList().toString() : "null";
            log.info("DEBUG: outputFile={}, exists={}, outputDirFiles={}, prompts={}, responses={}", 
                    outputFile, Files.exists(outputFile), fileNames, setup.prompts.size(), setup.responses.size());
            
            for (int i = 0; i < 10 && !Files.exists(outputFile); i++) {
                Thread.sleep(500);
            }
            
            // Re-check after retry loop
            files = outputDir.toFile().listFiles();
            fileNames = files != null ? java.util.Arrays.stream(files).map(f -> f.getName()).toList().toString() : "null";
            log.info("DEBUG AFTER RETRY: outputFile={}, exists={}, outputDirFiles={}", 
                    outputFile, Files.exists(outputFile), fileNames);
            
            assertThat(Files.exists(outputFile))
                    .withFailMessage("Output file was not created: {}. Output dir files: {}", outputFile, fileNames)
                    .isTrue();
            
            assertThat(Files.readAllBytes(outputFile))
                    .contains("## Analysis".getBytes());

            log.info("Pipeline integration test PASSED: {} LLM call(s), output file created", setup.prompts.size());
        } finally {
            setup.scanner.destroy();
            Files.walk(tempDir)
                    .sorted((a, b) -> b.compareTo(a))
                    .forEach(p -> {
                        try { Files.delete(p); } catch (IOException e) { /* ignore */ }
                    });
        }
    }

    /**
     * Test that the scanner detects file modifications, not just creation.
     */
    @Test
    void givenExistingFile_WhenFileModified_ThenLLMIsCalledForUpdate() throws Exception {
        log.info("Testing file modification detection in pipeline");

        Path tempDir = Files.createTempDirectory("agent-pipeline-mod-");
        Path inputDir = Files.createDirectory(tempDir.resolve("input"));
        Files.createDirectory(tempDir.resolve("output"));

        FileMetadataDatabase modDb = createSmartDb();
        ScannerObserverUseCase modObserver = new ScannerObserverUseCase(path -> 0L);
        Scanner modScanner = new Scanner("test-agent",
                inputDir.toString(),
                Duration.ofMillis(500),
                Duration.ZERO,
                modDb,
                modObserver);

        CopyOnWriteArrayList<String> modPrompts = new CopyOnWriteArrayList<>();
        AtomicReference<CountDownLatch> modLatchRef = new AtomicReference<>(new CountDownLatch(1));

        String mockResponse = "## Analysis\n\nDocument processed successfully.";
        ChatClient modChatClient = ChatClientMockBuilder.createMock(mockResponse);

        ChatClient.ChatClientRequestSpec modReqSpec = Mockito.mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.StreamResponseSpec modStreamSpec = Mockito.mock(ChatClient.StreamResponseSpec.class);
        Mockito.when(modChatClient.prompt(Mockito.anyString())).thenAnswer(inv -> {
            modPrompts.add(inv.getArgument(0, String.class));
            return modReqSpec;
        });
        Mockito.when(modReqSpec.stream()).thenReturn(modStreamSpec);
        Mockito.when(modStreamSpec.content()).thenReturn(Flux.just(mockResponse));

        AgentDefinition agentDef = new AgentDefinition(
                "(?:.*/)?(?<name>[^/]+\\.md)",
                "MOD-TEST-AGENT",
                "Map",
                "Analyze the document.",
                "Provide analysis.",
                "analysis-${name}",
                inputDir.toString());

        Flux<PromptResponse> modPipeline = new AgentConfigurator(
                modScanner.flux(),
                modChatClient,
                response -> modLatchRef.get().countDown())
                .configure(agentDef);

        modPipeline.subscribe();
        Thread.sleep(1000); // Let scanner start

        // Place initial file
        String testFileName = "modify-test.md";
        Files.writeString(inputDir.resolve(testFileName), "# Initial\n\nFirst version.");

        boolean initialProcessed = modLatchRef.get().await(10, TimeUnit.SECONDS);
        assertThat(initialProcessed).as("Initial file should be processed").isTrue();
        assertThat(modPrompts).hasSize(1);

        // Reset for modification
        modLatchRef.set(new CountDownLatch(1));

        // Modify the file
        Files.writeString(inputDir.resolve(testFileName), "# Modified\n\nSecond version.");
        log.info("Modified file: {}", inputDir.resolve(testFileName));

        // Wait for modification processing
        boolean modifiedProcessed = modLatchRef.get().await(15, TimeUnit.SECONDS);
        assertThat(modifiedProcessed)
                .withFailMessage("File modification was not detected within 15 seconds. "
                        + "Captured {} LLM calls: {}", modPrompts.size(), modPrompts)
                .isTrue();

        // Verify LLM was called again
        assertThat(modPrompts)
                .hasSizeGreaterThanOrEqualTo(2)
                .withFailMessage("Expected at least 2 LLM calls (initial + modification), got %d", modPrompts.size());

        modScanner.destroy();
        Files.walk(tempDir)
                .sorted((a, b) -> b.compareTo(a))
                .forEach(p -> {
                    try { Files.delete(p); } catch (IOException e) { /* ignore */ }
                });
        log.info("File modification test PASSED: {} total LLM call(s)", modPrompts.size());
    }

    /**
     * Test that the pipeline handles multiple files correctly.
     */
    @Test
    void givenMultipleFiles_WhenPlacedInWatchedDir_ThenAllAreProcessed() throws Exception {
        log.info("Testing multiple file processing in pipeline");

        Path tempDir = Files.createTempDirectory("agent-pipeline-multi-");
        Path inputDir = Files.createDirectory(tempDir.resolve("input"));
        Files.createDirectory(tempDir.resolve("output"));

        FileMetadataDatabase multiDb = createSmartDb();
        ScannerObserverUseCase multiObserver = new ScannerObserverUseCase(path -> 0L);
        Scanner multiScanner = new Scanner("test-agent",
                inputDir.toString(),
                Duration.ofMillis(500),
                Duration.ZERO,
                multiDb,
                multiObserver);

        CopyOnWriteArrayList<String> multiPrompts = new CopyOnWriteArrayList<>();
        CountDownLatch multiLatch = new CountDownLatch(3); // Wait for 3 files

        String mockResponse = "## Analysis\n\nDocument processed successfully.";
        ChatClient multiChatClient = ChatClientMockBuilder.createMock(mockResponse);

        ChatClient.ChatClientRequestSpec multiReqSpec = Mockito.mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.StreamResponseSpec multiStreamSpec = Mockito.mock(ChatClient.StreamResponseSpec.class);
        Mockito.when(multiChatClient.prompt(Mockito.anyString())).thenAnswer(inv -> {
            multiPrompts.add(inv.getArgument(0, String.class));
            return multiReqSpec;
        });
        Mockito.when(multiReqSpec.stream()).thenReturn(multiStreamSpec);
        Mockito.when(multiStreamSpec.content()).thenReturn(Flux.just(mockResponse));

        AgentDefinition agentDef = new AgentDefinition(
                "(?:.*/)?(?<name>[^/]+\\.md)",
                "MULTI-TEST-AGENT",
                "Map",
                "Analyze the document.",
                "Provide analysis.",
                "analysis-${name}",
                inputDir.toString());

        Flux<PromptResponse> multiPipeline = new AgentConfigurator(
                multiScanner.flux(),
                multiChatClient,
                response -> multiLatch.countDown())
                .configure(agentDef);

        multiPipeline.subscribe();
        Thread.sleep(1000); // Let scanner start

        // Place multiple files (stagger to avoid race conditions)
        String[] fileNames = {"doc-a.md", "doc-b.md", "doc-c.md"};
        for (int i = 0; i < fileNames.length; i++) {
            Files.writeString(inputDir.resolve(fileNames[i]), "# Doc " + (i + 1) + "\n\nContent " + (i + 1));
            Thread.sleep(200);
        }

        // Wait for all files to be processed
        boolean allProcessed = multiLatch.await(20, TimeUnit.SECONDS);
        assertThat(allProcessed)
                .withFailMessage("Not all files were processed within 20 seconds. "
                        + "Captured %d LLM calls: %s", multiPrompts.size(), multiPrompts)
                .isTrue();

        // Verify at least 3 LLM calls were made (one per file)
        assertThat(multiPrompts)
                .hasSizeGreaterThanOrEqualTo(3)
                .withFailMessage("Expected at least 3 LLM calls for 3 files, got %d", multiPrompts.size());

        multiScanner.destroy();
        Files.walk(tempDir)
                .sorted((a, b) -> b.compareTo(a))
                .forEach(p -> {
                    try { Files.delete(p); } catch (IOException e) { /* ignore */ }
                });
        log.info("Multiple file test PASSED: {} LLM call(s) for {} files", multiPrompts.size(), fileNames.length);
    }
}

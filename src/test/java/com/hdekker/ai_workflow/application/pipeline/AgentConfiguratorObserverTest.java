package com.hdekker.ai_workflow.application.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.hdekker.ai_workflow.TestData;
import com.hdekker.ai_workflow.application.agent.port.FileWritePort;
import com.hdekker.ai_workflow.application.pipeline.port.AgentObserverEventPort;
import com.hdekker.ai_workflow.application.pipeline.port.AgentObserverPort;
import com.hdekker.ai_workflow.domain.agent.AgentDefinition;
import com.hdekker.ai_workflow.domain.agent.AgentType;
import com.hdekker.ai_workflow.domain.file.FileHistory;
import com.hdekker.ai_workflow.domain.file.FileMetadata;
import com.hdekker.ai_workflow.domain.pipeline.AgentObserverEvent;
import com.hdekker.ai_workflow.domain.pipeline.AgentObserverEventType;
import com.hdekker.ai_workflow.domain.prompt.PromptResponse;
import com.hdekker.ai_workflow.domain.shared.FileHash;
import com.hdekker.ai_workflow.test.harness.mock.ChatClientMockBuilder;

import org.springframework.ai.chat.client.ChatClient;
import reactor.core.publisher.Flux;

/**
 * Integration tests for agent observer wiring into {@link AgentConfigurator}.
 * <p>
 * Verifies that:
 * <ul>
 *   <li>Dispatch hook fires when LLM response flows through pipeline</li>
 *   <li>Storage hook fires when response is persisted</li>
 *   <li>Both hooks fire in correct sequence (dispatch before storage)</li>
 *   <li>Observer can be null (backward compatibility for existing callers)</li>
 * </ul>
 */
class AgentConfiguratorObserverTest {

    private Path tempDir;
    private Path inputDir;
    private Path outputDir;

    private AgentObserverUseCase observer;
    private ChatClient chatClient;

    private CopyOnWriteArrayList<AgentObserverEvent> dispatchedEvents = new CopyOnWriteArrayList<>();
    private CopyOnWriteArrayList<AgentObserverEvent> storedEvents = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        // Create temp directories
        tempDir = Files.createTempDirectory("observer-integration-");
        inputDir = Files.createDirectory(tempDir.resolve("input"));
        outputDir = Files.createDirectory(tempDir.resolve("output"));

        // Set up observer with real event bus for integration verification
        AgentObserverEventBus realEventBus = new AgentObserverEventBus();
        realEventBus.registerCallback(event -> {
            if (event.eventType() == AgentObserverEventType.DISPATCHED) {
                dispatchedEvents.add(event);
            } else if (event.eventType() == AgentObserverEventType.STORED) {
                storedEvents.add(event);
            }
        });

        AgentObserverPort testMetrics = mock(AgentObserverPort.class);
        when(testMetrics.getDispatchCount(any())).thenReturn(0L);
        when(testMetrics.getTotalDispatchCount()).thenReturn(0L);
        when(testMetrics.getStorageCount(any())).thenReturn(0L);
        when(testMetrics.getTotalStorageCount()).thenReturn(0L);

        observer = new AgentObserverUseCase(testMetrics, realEventBus);

        // Set up chat client mock
        chatClient = ChatClientMockBuilder.createMock("Analysis: File processed successfully.");
    }

    @AfterEach
    void tearDown() throws Exception {
        if (tempDir != null && Files.exists(tempDir)) {
            Files.walk(tempDir)
                    .sorted((a, b) -> b.compareTo(a))
                    .forEach(p -> {
                        try {
                            Files.delete(p);
                        } catch (Exception e) {
                            // ignore
                        }
                    });
        }
    }

    private FileHistory createFileHistory(String fileName, String content) {
        return new FileHistory(
                new FileMetadata(
                        inputDir.resolve(fileName).toString(),
                        content,
                        FileHash.hash(content)),
                Optional.empty());
    }

    private AgentDefinition basicAgent(String fileName) {
        return new AgentDefinition(
                "(?:.*/)?(?<name>.*\\.txt)",
                "OBSERVER-TEST-AGENT",
                "Process the provided file.",
                AgentType.MAP,
                "Provide a concise analysis.",
                "output/${name}",
                inputDir.toString());
    }

    private AgentConfigurator createConfigurator(Flux<FileHistory> fileFlux) {
        return createConfigurator(fileFlux, observer);
    }

    private AgentConfigurator createConfigurator(
            Flux<FileHistory> fileFlux,
            AgentObserverUseCase obs) {
        Path finalOutputDir = outputDir;
        java.util.function.Consumer<PromptResponse> persister = response -> {
            try {
                Path outputPath = finalOutputDir.resolve(response.createOutputFileName());
                Files.createDirectories(outputPath.getParent());
                Files.writeString(outputPath, response.response());
            } catch (Exception e) {
                throw new RuntimeException("Persist failed: " + response.fileName(), e);
            }
        };

        return new AgentConfigurator(
                fileFlux,
                chatClient,
                persister,
                null,
                obs);
    }

    // -- Task 2.6: Dispatch hook fires --

    @Test
    void givenAgentPipeline_WhenPipelineExecutes_ThenDispatchHookFires() {
        dispatchedEvents.clear();
        storedEvents.clear();

        FileHistory fileHistory = createFileHistory("dispatch-test.txt", "Dispatch hook test content.");

        AgentConfigurator configurator = createConfigurator(Flux.just(fileHistory));

        AgentDefinition agent = basicAgent("dispatch-test.txt");

        Flux<PromptResponse> pipeline = configurator.configure(agent);
        PromptResponse response = pipeline.blockFirst(Duration.ofSeconds(5));

        // Verify dispatch event was recorded
        assertThat(response).isNotNull();
        assertThat(dispatchedEvents)
                .as("Dispatch hook should fire when LLM response flows through pipeline")
                .hasSize(1);

        AgentObserverEvent dispatchEvent = dispatchedEvents.get(0);
        assertThat(dispatchEvent.eventType()).isEqualTo(AgentObserverEventType.DISPATCHED);
        // fileName() in PromptResponse is the full path from PromptRequest.fileURL()
        assertThat(dispatchEvent.fileName())
                .isEqualTo(inputDir.resolve("dispatch-test.txt").toString());
        assertThat(dispatchEvent.agentId()).isEqualTo(agent.title());
    }

    @Test
    void givenAgentPipeline_WhenPipelineExecutes_ThenMetricsPortRecordsDispatch() {
        dispatchedEvents.clear();
        storedEvents.clear();

        AgentObserverPort capturingMetrics = mock(AgentObserverPort.class);
        when(capturingMetrics.getDispatchCount(any())).thenReturn(0L);
        when(capturingMetrics.getTotalDispatchCount()).thenReturn(0L);
        when(capturingMetrics.getStorageCount(any())).thenReturn(0L);
        when(capturingMetrics.getTotalStorageCount()).thenReturn(0L);

        AgentObserverEventBus eventBus = new AgentObserverEventBus();
        AgentObserverUseCase useCase = new AgentObserverUseCase(capturingMetrics, eventBus);

        FileHistory fileHistory = createFileHistory("metrics-dispatch.txt", "Metrics dispatch test.");

        AgentConfigurator configurator = createConfigurator(Flux.just(fileHistory), useCase);

        AgentDefinition agent = basicAgent("metrics-dispatch.txt");
        Flux<PromptResponse> pipeline = configurator.configure(agent);
        pipeline.blockFirst(Duration.ofSeconds(5));

        // Verify metrics port recorded the dispatch with matching agent and fileName
        Path expectedFile = inputDir.resolve("metrics-dispatch.txt");
        verify(capturingMetrics).recordDispatch(eq(agent.title()), eq(expectedFile.toString()));
    }

    @Test
    void whenRecordStorageWithNullPath_ThenMetricsPortRecordsStorage() {
        dispatchedEvents.clear();
        storedEvents.clear();

        AgentObserverPort capturingMetrics = mock(AgentObserverPort.class);
        when(capturingMetrics.getDispatchCount(any())).thenReturn(0L);
        when(capturingMetrics.getTotalDispatchCount()).thenReturn(0L);
        when(capturingMetrics.getStorageCount(any())).thenReturn(0L);
        when(capturingMetrics.getTotalStorageCount()).thenReturn(0L);

        AgentObserverEventBus eventBus = new AgentObserverEventBus();
        AgentObserverUseCase useCase = new AgentObserverUseCase(capturingMetrics, eventBus);

        FileHistory fileHistory = createFileHistory("null-path-storage.txt", "Null path storage test.");

        AgentConfigurator configurator = createConfigurator(Flux.just(fileHistory), useCase);

        AgentDefinition agent = basicAgent("null-path-storage.txt");
        Flux<PromptResponse> pipeline = configurator.configure(agent);
        pipeline.blockFirst(Duration.ofSeconds(5));

        // Verify storage was recorded with null path arg
        Path expectedFile = inputDir.resolve("null-path-storage.txt");
        verify(capturingMetrics).recordStorage(
                eq(agent.title()),
                eq(expectedFile.toString()),
                argThat(p -> p == null));
    }

    private static <T> T eq(T value) {
        return argThat(actual -> {
            if (value == null) {
                return actual == null;
            }
            return value.equals(actual);
        });
    }

    // -- Task 2.7: Storage hook fires --

    @Test
    void givenAgentPipeline_WhenPipelineExecutes_ThenStorageHookFires() {
        dispatchedEvents.clear();
        storedEvents.clear();

        FileHistory fileHistory = createFileHistory("storage-test.txt", "Storage hook test content.");

        AgentConfigurator configurator = createConfigurator(Flux.just(fileHistory));

        AgentDefinition agent = basicAgent("storage-test.txt");

        Flux<PromptResponse> pipeline = configurator.configure(agent);
        PromptResponse response = pipeline.blockFirst(Duration.ofSeconds(5));

        // Verify storage event was recorded
        assertThat(response).isNotNull();
        assertThat(storedEvents)
                .as("Storage hook should fire when response is persisted")
                .hasSize(1);

        AgentObserverEvent storageEvent = storedEvents.get(0);
        assertThat(storageEvent.eventType()).isEqualTo(AgentObserverEventType.STORED);
        // fileName() in PromptResponse is the full path
        assertThat(storageEvent.fileName())
                .isEqualTo(inputDir.resolve("storage-test.txt").toString());
        assertThat(storageEvent.agentId()).isEqualTo(agent.title());

        // Verify output file was actually created
        Path outputPath = outputDir.resolve("output/storage-test.txt");
        assertThat(outputPath).exists();
    }

    @Test
    void givenAgentPipeline_WhenPipelineExecutes_ThenOutputFileCreated() {
        dispatchedEvents.clear();
        storedEvents.clear();

        FileHistory fileHistory = createFileHistory("file-created.txt", "File creation test content.");

        AgentConfigurator configurator = createConfigurator(Flux.just(fileHistory));

        AgentDefinition agent = basicAgent("file-created.txt");

        Flux<PromptResponse> pipeline = configurator.configure(agent);
        PromptResponse response = pipeline.blockFirst(Duration.ofSeconds(5));

        assertThat(response).isNotNull();

        Path outputPath = outputDir.resolve("output/file-created.txt");
        assertThat(outputPath).exists();
        assertThat(outputPath).isRegularFile();
        try {
            assertThat(Files.readString(outputPath))
                    .isEqualToIgnoringWhitespace(response.response());
        } catch (Exception e) {
            throw new RuntimeException("Failed to read output file", e);
        }
    }

    // -- Task 2.8: Both hooks fire in sequence --

    @Test
    void givenAgentPipeline_WhenPipelineExecutes_ThenDispatchBeforeStorage() {
        dispatchedEvents.clear();
        storedEvents.clear();

        // Use a sequential event log to track order
        CopyOnWriteArrayList<String> eventOrder = new CopyOnWriteArrayList<>();

        AgentObserverEventBus orderedEventBus = new AgentObserverEventBus();
        orderedEventBus.registerCallback(event -> {
            String action = event.eventType() == AgentObserverEventType.DISPATCHED
                    ? "DISPATCH"
                    : "STORE";
            eventOrder.add(action);
        });

        AgentObserverPort capturingMetrics = mock(AgentObserverPort.class);
        when(capturingMetrics.getDispatchCount(any())).thenReturn(0L);
        when(capturingMetrics.getTotalDispatchCount()).thenReturn(0L);
        when(capturingMetrics.getStorageCount(any())).thenReturn(0L);
        when(capturingMetrics.getTotalStorageCount()).thenReturn(0L);

        AgentObserverUseCase useCase = new AgentObserverUseCase(capturingMetrics, orderedEventBus);

        FileHistory fileHistory = createFileHistory("sequence-test.txt", "Sequence test content.");

        AgentConfigurator configurator = createConfigurator(Flux.just(fileHistory), useCase);

        AgentDefinition agent = basicAgent("sequence-test.txt");
        Flux<PromptResponse> pipeline = configurator.configure(agent);
        pipeline.blockFirst(Duration.ofSeconds(5));

        // Verify sequence: DISPATCH comes before STORE
        assertThat(eventOrder)
                .as("Dispatch should fire before storage")
                .hasSize(2);
        assertThat(eventOrder.get(0)).isEqualTo("DISPATCH");
        assertThat(eventOrder.get(1)).isEqualTo("STORE");
    }

    @Test
    void givenAgentPipeline_WhenPipelineExecutes_ThenBothDispatchAndStorageCountersIncrement() {
        dispatchedEvents.clear();
        storedEvents.clear();

        FileHistory fileHistory = createFileHistory("both-counters.txt", "Both counters test.");

        AgentConfigurator configurator = createConfigurator(Flux.just(fileHistory));

        AgentDefinition agent = basicAgent("both-counters.txt");
        Flux<PromptResponse> pipeline = configurator.configure(agent);
        PromptResponse response = pipeline.blockFirst(Duration.ofSeconds(5));

        assertThat(response).isNotNull();
        assertThat(dispatchedEvents).hasSize(1);
        assertThat(storedEvents).hasSize(1);

        // Both events should reference the same agent
        assertThat(dispatchedEvents.get(0).agentId()).isEqualTo(agent.title());
        assertThat(storedEvents.get(0).agentId()).isEqualTo(agent.title());
    }

    // -- Task 2.5: Null observer backward compatibility --

    @Test
    void givenNullObserver_WhenPipelineExecutes_ThenPipelineCompletesNormally() {
        FileHistory fileHistory = createFileHistory("no-observer.txt", "No observer test content.");

        Path finalOutputDir = outputDir;
        java.util.function.Consumer<PromptResponse> persister = response -> {
            try {
                Path outputPath = finalOutputDir.resolve(response.createOutputFileName());
                Files.createDirectories(outputPath.getParent());
                Files.writeString(outputPath, response.response());
            } catch (Exception e) {
                throw new RuntimeException("Persist failed", e);
            }
        };

        AgentConfigurator noObserverConfigurator = new AgentConfigurator(
                Flux.just(fileHistory),
                chatClient,
                persister,
                null,
                null); // null observer

        AgentDefinition agent = basicAgent("no-observer.txt");

        Flux<PromptResponse> pipeline = noObserverConfigurator.configure(agent);
        PromptResponse response = pipeline.blockFirst(Duration.ofSeconds(5));

        // Pipeline should complete successfully with no observer
        assertThat(response).isNotNull();
        assertThat(response.response()).isNotBlank();

        // Output file should still be created
        Path outputPath = outputDir.resolve("output/no-observer.txt");
        assertThat(outputPath).exists();
    }

    // -- Observer with FileWritePort --

    @Test
    void givenObserverAndFileWritePort_WhenPipelineExecutes_ThenObserverReceivesEvents() {
        dispatchedEvents.clear();
        storedEvents.clear();

        // Set up a FileWritePort mock
        FileWritePort fileWritePort = mock(FileWritePort.class);
        Path finalOutputDir = outputDir;
        when(fileWritePort.createPersister(any())).thenAnswer(inv ->
                (java.util.function.Consumer<PromptResponse>) response -> {
                    try {
                        Path outputPath = finalOutputDir.resolve(response.createOutputFileName());
                        Files.createDirectories(outputPath.getParent());
                        Files.writeString(outputPath, response.response());
                    } catch (Exception e) {
                        throw new RuntimeException("FileWritePort persist failed", e);
                    }
                }
        );

        AgentObserverEventBus eventBus = new AgentObserverEventBus();
        AgentObserverPort testMetrics = mock(AgentObserverPort.class);
        when(testMetrics.getDispatchCount(any())).thenReturn(0L);
        when(testMetrics.getTotalDispatchCount()).thenReturn(0L);
        when(testMetrics.getStorageCount(any())).thenReturn(0L);
        when(testMetrics.getTotalStorageCount()).thenReturn(0L);

        AgentObserverUseCase testObserver = new AgentObserverUseCase(testMetrics, eventBus);

        FileHistory fileHistory = createFileHistory("with-port.txt", "FileWritePort test content.");

        AgentConfigurator configuratorWithPort = new AgentConfigurator(
                Flux.just(fileHistory),
                chatClient,
                null, // persister not used when fileWritePort is set
                fileWritePort,
                testObserver);

        AgentDefinition agent = basicAgent("with-port.txt");

        Flux<PromptResponse> pipeline = configuratorWithPort.configure(agent);
        PromptResponse response = pipeline.blockFirst(Duration.ofSeconds(5));

        assertThat(response).isNotNull();

        // Verify dispatch was recorded
        Path expectedDispatchFile = inputDir.resolve("with-port.txt");
        verify(testMetrics).recordDispatch(eq(agent.title()), eq(expectedDispatchFile.toString()));

        // Verify storage was recorded with null path arg
        verify(testMetrics).recordStorage(
                eq(agent.title()),
                eq(expectedDispatchFile.toString()),
                argThat(p -> p == null));
    }

    // -- Multiple files through pipeline --

    @Test
    void givenMultipleFiles_WhenPipelineExecutes_ThenObserverCountsAllFiles() {
        dispatchedEvents.clear();
        storedEvents.clear();

        // Create multiple file histories
        FileHistory file1 = createFileHistory("multi-file-1.txt", "First test file content.");
        FileHistory file2 = createFileHistory("multi-file-2.txt", "Second test file content.");

        AtomicInteger callCount = new AtomicInteger();
        ChatClient multiChatClient = ChatClientMockBuilder.createMock(
                "Analysis: File " + callCount.incrementAndGet() + " processed.");

        AgentConfigurator multiConfigurator = new AgentConfigurator(
                Flux.just(file1, file2),
                multiChatClient,
                response -> {
                    try {
                        Path outputPath = outputDir.resolve(response.createOutputFileName());
                        Files.createDirectories(outputPath.getParent());
                        Files.writeString(outputPath, response.response());
                    } catch (Exception e) {
                        throw new RuntimeException("Persist failed", e);
                    }
                },
                null,
                observer);

        AgentDefinition agent = TestData.basicPrompt();

        Flux<PromptResponse> pipeline = multiConfigurator.configure(agent);
        java.util.List<PromptResponse> responses = pipeline.collectList().block(Duration.ofSeconds(5));

        assertThat(responses).hasSize(2);
        assertThat(dispatchedEvents).hasSize(2);
        assertThat(storedEvents).hasSize(2);
    }
}

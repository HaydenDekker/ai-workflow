package com.hdekker.ai_workflow.application.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.hdekker.ai_workflow.application.pipeline.port.AgentObserverEventPort;
import com.hdekker.ai_workflow.application.pipeline.port.AgentObserverPort;
import com.hdekker.ai_workflow.domain.pipeline.AgentMetrics;
import com.hdekker.ai_workflow.domain.pipeline.AgentObserverEvent;
import com.hdekker.ai_workflow.domain.pipeline.AgentObserverEventType;
import com.hdekker.ai_workflow.domain.pipeline.RegexFilterEntry;

/**
 * Unit tests for {@link AgentObserverUseCase}.
 * <p>
 * Verifies that the use case correctly orchestrates both the metrics port
 * and the event bus for dispatch and storage recording, and that query
 * methods delegate to the metrics port only.
 */
class AgentObserverUseCaseTest {

    private AgentObserverPort metricsPort;
    private AgentObserverEventPort eventPort;
    private AgentObserverUseCase useCase;

    @BeforeEach
    void setUp() {
        metricsPort = mock(AgentObserverPort.class);
        eventPort = mock(AgentObserverEventPort.class);
        useCase = new AgentObserverUseCase(metricsPort, eventPort);
    }

    // -- recordDispatch tests --

    @Test
    void givenAgentAndFileName_WhenRecordDispatch_ThenMetricsRecordedAndEventPublished() {
        useCase.recordDispatch("agent-1", "test.txt");

        verify(metricsPort).recordDispatch("agent-1", "test.txt");
        verify(eventPort).publish(any(AgentObserverEvent.class));
    }

    @Test
    void whenRecordDispatch_ThenEventIsOfDispatchedType() {
        AtomicReference<AgentObserverEvent> capturedEvent = new AtomicReference<>();
        when(metricsPort.getDispatchCount("agent-1")).thenReturn(0L);
        when(metricsPort.getTotalDispatchCount()).thenReturn(0L);

        useCase.recordDispatch("agent-1", "input.txt");

        // Verify the event published is DISPATCHED
        verify(eventPort).publish(any(AgentObserverEvent.class));
    }

    @Test
    void givenMultipleDispatches_WhenRecorded_ThenMetricsAndEventCalledEachTime() {
        useCase.recordDispatch("agent-1", "file1.txt");
        useCase.recordDispatch("agent-1", "file2.txt");
        useCase.recordDispatch("agent-2", "file3.txt");

        verify(metricsPort, times(3)).recordDispatch(any(String.class), any(String.class));
        verify(eventPort, times(3)).publish(any(AgentObserverEvent.class));
    }

    // -- recordStorage tests --

    @Test
    void givenAgentAndOutput_WhenRecordStorage_ThenMetricsRecordedAndEventPublished() {
        useCase.recordStorage("agent-1", "output.txt", Paths.get("/tmp/output.txt"));

        verify(metricsPort).recordStorage("agent-1", "output.txt", Paths.get("/tmp/output.txt"));
        verify(eventPort).publish(any(AgentObserverEvent.class));
    }

    @Test
    void givenAgentAndOutput_WhenRecordStorageWithNullPath_ThenMetricsRecordedAndEventPublished() {
        useCase.recordStorage("agent-1", "output.txt", null);

        verify(metricsPort).recordStorage("agent-1", "output.txt", null);
        verify(eventPort).publish(any(AgentObserverEvent.class));
    }

    @Test
    void givenMultipleStorages_WhenRecorded_ThenMetricsAndEventCalledEachTime() {
        useCase.recordStorage("agent-1", "out1.txt", Paths.get("/tmp/out1.txt"));
        useCase.recordStorage("agent-1", "out2.txt", Paths.get("/tmp/out2.txt"));

        verify(metricsPort, times(2)).recordStorage(any(String.class), any(String.class),
                any(java.nio.file.Path.class));
        verify(eventPort, times(2)).publish(any(AgentObserverEvent.class));
    }

    // -- dispatch count query tests --

    @Test
    void whenGetDispatchCount_ThenDelegatesToMetricsPort() {
        when(metricsPort.getDispatchCount("agent-1")).thenReturn(42L);

        long result = useCase.getDispatchCount("agent-1");

        assertThat(result).isEqualTo(42L);
        verify(metricsPort).getDispatchCount("agent-1");
    }

    @Test
    void whenGetDispatchCount_NoEventPortInteraction() {
        when(metricsPort.getDispatchCount("agent-1")).thenReturn(0L);

        useCase.getDispatchCount("agent-1");

        verifyNoMoreInteractions(eventPort);
    }

    // -- total dispatch count query tests --

    @Test
    void whenGetTotalDispatchCount_ThenDelegatesToMetricsPort() {
        when(metricsPort.getTotalDispatchCount()).thenReturn(100L);

        long result = useCase.getTotalDispatchCount();

        assertThat(result).isEqualTo(100L);
        verify(metricsPort).getTotalDispatchCount();
    }

    @Test
    void whenGetTotalDispatchCount_NoEventPortInteraction() {
        when(metricsPort.getTotalDispatchCount()).thenReturn(0L);

        useCase.getTotalDispatchCount();

        verifyNoMoreInteractions(eventPort);
    }

    // -- storage count query tests --

    @Test
    void whenGetStorageCount_ThenDelegatesToMetricsPort() {
        when(metricsPort.getStorageCount("agent-1")).thenReturn(7L);

        long result = useCase.getStorageCount("agent-1");

        assertThat(result).isEqualTo(7L);
        verify(metricsPort).getStorageCount("agent-1");
    }

    @Test
    void whenGetStorageCount_NoEventPortInteraction() {
        when(metricsPort.getStorageCount("agent-1")).thenReturn(0L);

        useCase.getStorageCount("agent-1");

        verifyNoMoreInteractions(eventPort);
    }

    // -- total storage count query tests --

    @Test
    void whenGetTotalStorageCount_ThenDelegatesToMetricsPort() {
        when(metricsPort.getTotalStorageCount()).thenReturn(55L);

        long result = useCase.getTotalStorageCount();

        assertThat(result).isEqualTo(55L);
        verify(metricsPort).getTotalStorageCount();
    }

    @Test
    void whenGetTotalStorageCount_NoEventPortInteraction() {
        when(metricsPort.getTotalStorageCount()).thenReturn(0L);

        useCase.getTotalStorageCount();

        verifyNoMoreInteractions(eventPort);
    }

    // -- integration-style test with real event bus --

    @Test
    void givenRealEventBus_WhenRecordDispatch_ThenCallbackReceivesDispatchedEvent() {
        AgentObserverEventBus realEventBus = new AgentObserverEventBus();
        useCase = new AgentObserverUseCase(metricsPort, realEventBus);

        AtomicReference<AgentObserverEvent> receivedEvent = new AtomicReference<>();
        realEventBus.registerCallback(receivedEvent::set);

        useCase.recordDispatch("agent-1", "input.txt");

        assertThat(receivedEvent.get()).isNotNull();
        assertThat(receivedEvent.get().agentId()).isEqualTo("agent-1");
        assertThat(receivedEvent.get().eventType()).isEqualTo(AgentObserverEventType.DISPATCHED);
        assertThat(receivedEvent.get().fileName()).isEqualTo("input.txt");
        assertThat(receivedEvent.get().timestamp()).isNotNull();
    }

    @Test
    void givenRealEventBus_WhenRecordStorage_ThenCallbackReceivesStoredEvent() {
        AgentObserverEventBus realEventBus = new AgentObserverEventBus();
        useCase = new AgentObserverUseCase(metricsPort, realEventBus);

        AtomicReference<AgentObserverEvent> receivedEvent = new AtomicReference<>();
        realEventBus.registerCallback(receivedEvent::set);

        useCase.recordStorage("agent-1", "output.txt", Paths.get("/tmp/output.txt"));

        assertThat(receivedEvent.get()).isNotNull();
        assertThat(receivedEvent.get().agentId()).isEqualTo("agent-1");
        assertThat(receivedEvent.get().eventType()).isEqualTo(AgentObserverEventType.STORED);
        assertThat(receivedEvent.get().fileName()).isEqualTo("output.txt");
        assertThat(receivedEvent.get().timestamp()).isNotNull();
    }

    // -- mixed dispatch and storage orchestration --

    @Test
    void givenDispatchThenStorage_ThenBothPortsCalledInOrder() {
        CopyOnWriteArrayList<String> actions = new CopyOnWriteArrayList<>();

        doAnswer(inv -> { actions.add("metrics-dispatch"); return null; })
                .when(metricsPort).recordDispatch("agent-1", "input.txt");
        doAnswer(inv -> { actions.add("metrics-storage"); return null; })
                .when(metricsPort).recordStorage("agent-1", "output.txt",
                        Paths.get("/tmp/out.txt"));

        useCase.recordDispatch("agent-1", "input.txt");
        useCase.recordStorage("agent-1", "output.txt", Paths.get("/tmp/out.txt"));

        assertThat(actions).containsExactly("metrics-dispatch", "metrics-storage");
        verify(eventPort, times(2)).publish(any(AgentObserverEvent.class));
    }

    // -- null agent id handling --

    @Test
    void givenNullAgentId_WhenRecordDispatch_ThenStillDispatchesToMetrics() {
        useCase.recordDispatch(null, "test.txt");

        verify(metricsPort).recordDispatch((String) any(), eq("test.txt"));
        verify(eventPort).publish(any(AgentObserverEvent.class));
    }

    // -- multiple agents independent counters --

    @Test
    void whenRecordAcrossAgents_ThenMetricsDelegatedCorrectly() {
        when(metricsPort.getDispatchCount("agent-a")).thenReturn(3L);
        when(metricsPort.getDispatchCount("agent-b")).thenReturn(5L);
        when(metricsPort.getTotalDispatchCount()).thenReturn(8L);

        useCase.getDispatchCount("agent-a");
        useCase.getDispatchCount("agent-b");
        useCase.getTotalDispatchCount();

        verify(metricsPort).getDispatchCount("agent-a");
        verify(metricsPort).getDispatchCount("agent-b");
        verify(metricsPort).getTotalDispatchCount();
    }

    // -- output directory file count tests --

    @Test
    void whenGetOutputDirectoryFileCount_ThenDelegatesToMetricsPort() {
        when(metricsPort.getOutputDirectoryFileCount()).thenReturn(25L);

        long result = useCase.getOutputDirectoryFileCount();

        assertThat(result).isEqualTo(25L);
        verify(metricsPort).getOutputDirectoryFileCount();
    }

    @Test
    void whenGetOutputDirectoryFileCount_NoEventPortInteraction() {
        when(metricsPort.getOutputDirectoryFileCount()).thenReturn(0L);

        useCase.getOutputDirectoryFileCount();

        verifyNoMoreInteractions(eventPort);
    }

    @Test
    void whenGetOutputDirectoryFileCount_ZeroFiles_ThenReturnsZero() {
        when(metricsPort.getOutputDirectoryFileCount()).thenReturn(0L);

        long result = useCase.getOutputDirectoryFileCount();

        assertThat(result).isZero();
    }

    // -- recordFilter tests --

    @Test
    void givenRecordFilter_WhenCalled_ThenDelegatesToPort() {
        useCase.recordFilter("agent-1", "file.txt", ".*\\.java");

        verify(metricsPort).recordFilter("agent-1", "file.txt", ".*\\.java");
        verify(eventPort).publish(any(AgentObserverEvent.class));
    }

    @Test
    void whenRecordFilter_ThenEventIsOfFilteredType() {
        AtomicReference<AgentObserverEvent> capturedEvent = new AtomicReference<>();
        doAnswer(inv -> {
            capturedEvent.set(inv.getArgument(0));
            return null;
        }).when(eventPort).publish(any(AgentObserverEvent.class));

        useCase.recordFilter("agent-1", "notes.md", ".*\\.java");

        assertThat(capturedEvent.get()).isNotNull();
        assertThat(capturedEvent.get().eventType()).isEqualTo(AgentObserverEventType.FILTERED);
        assertThat(capturedEvent.get().agentId()).isEqualTo("agent-1");
        assertThat(capturedEvent.get().fileName()).isEqualTo("notes.md");
    }

    // -- filter count query tests --

    @Test
    void whenGetFilterCount_ThenDelegatesToMetricsPort() {
        when(metricsPort.getFilterCount("agent-1")).thenReturn(5L);

        long result = useCase.getFilterCount("agent-1");

        assertThat(result).isEqualTo(5L);
        verify(metricsPort).getFilterCount("agent-1");
    }

    @Test
    void whenGetFilterCount_NoEventPortInteraction() {
        when(metricsPort.getFilterCount("agent-1")).thenReturn(0L);

        useCase.getFilterCount("agent-1");

        verifyNoMoreInteractions(eventPort);
    }

    // -- total filter count query tests --

    @Test
    void whenGetTotalFilterCount_ThenDelegatesToMetricsPort() {
        when(metricsPort.getTotalFilterCount()).thenReturn(42L);

        long result = useCase.getTotalFilterCount();

        assertThat(result).isEqualTo(42L);
        verify(metricsPort).getTotalFilterCount();
    }

    @Test
    void whenGetTotalFilterCount_NoEventPortInteraction() {
        when(metricsPort.getTotalFilterCount()).thenReturn(0L);

        useCase.getTotalFilterCount();

        verifyNoMoreInteractions(eventPort);
    }

    // -- last filtered entries query tests --

    @Test
    void whenGetLastFilteredEntries_ThenDelegatesToMetricsPort() {
        List<RegexFilterEntry> entries = List.of(
                RegexFilterEntry.rejected("agent-1", "file1.md", ".*\\.java"),
                RegexFilterEntry.rejected("agent-1", "file2.md", ".*\\.java")
        );
        when(metricsPort.getLastFilteredEntries("agent-1")).thenReturn(entries);

        List<RegexFilterEntry> result = useCase.getLastFilteredEntries("agent-1");

        assertThat(result).hasSize(2);
        assertThat(result.get(0).fileUrl()).isEqualTo("file1.md");
        verify(metricsPort).getLastFilteredEntries("agent-1");
    }

    @Test
    void whenGetLastFilteredEntries_NoEventPortInteraction() {
        when(metricsPort.getLastFilteredEntries("agent-1")).thenReturn(List.of());

        useCase.getLastFilteredEntries("agent-1");

        verifyNoMoreInteractions(eventPort);
    }

    // -- AgentMetrics consolidated fetch tests --

    @Test
    void givenGetAgentMetrics_WhenCalled_ThenDelegatesToPort() {
        AgentMetrics expected = AgentMetrics.empty();
        when(metricsPort.getAgentMetrics("agent-1")).thenReturn(expected);

        AgentMetrics result = useCase.getAgentMetrics("agent-1");

        assertThat(result).isEqualTo(expected);
        verify(metricsPort).getAgentMetrics("agent-1");
    }

    @Test
    void whenGetAgentMetrics_NoEventPortInteraction() {
        when(metricsPort.getAgentMetrics("agent-1")).thenReturn(AgentMetrics.empty());

        useCase.getAgentMetrics("agent-1");

        verifyNoMoreInteractions(eventPort);
    }

    @Test
    void givenAgentMetricsWithData_WhenGetAgentMetrics_ThenReturnsCorrectData() {
        List<RegexFilterEntry> entries = List.of(
                RegexFilterEntry.rejected("agent-1", "notes.md", ".*\\.java")
        );
        AgentMetrics expected = new AgentMetrics(3L, 1L, entries);
        when(metricsPort.getAgentMetrics("agent-1")).thenReturn(expected);

        AgentMetrics result = useCase.getAgentMetrics("agent-1");

        assertThat(result.dispatchCount()).isEqualTo(3L);
        assertThat(result.filterCount()).isEqualTo(1L);
        assertThat(result.lastFilteredEntries()).hasSize(1);
    }
}

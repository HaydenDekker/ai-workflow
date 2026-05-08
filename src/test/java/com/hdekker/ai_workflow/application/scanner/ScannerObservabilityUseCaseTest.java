package com.hdekker.ai_workflow.application.scanner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.hdekker.ai_workflow.application.scanner.port.ScannerEventPort;
import com.hdekker.ai_workflow.application.scanner.port.ScannerMetricsPort;
import com.hdekker.ai_workflow.domain.scanner.ScannerEventType;
import com.hdekker.ai_workflow.domain.scanner.ScannerFileEvent;
import com.hdekker.ai_workflow.domain.scanner.ScannerFileResult;
import com.hdekker.ai_workflow.domain.scanner.ScannerMetrics;

/**
 * Unit tests for {@link ScannerObservabilityUseCase}.
 * <p>
 * Verifies that the use case correctly orchestrates both the metrics port
 * and the event bus for every public method.
 */
class ScannerObservabilityUseCaseTest {

    private ScannerMetricsPort metricsPort;
    private ScannerEventPort eventPort;
    private ScannerObservabilityUseCase useCase;

    @BeforeEach
    void setUp() {
        metricsPort = mock(ScannerMetricsPort.class);
        eventPort = mock(ScannerEventPort.class);
        useCase = new ScannerObservabilityUseCase(metricsPort, eventPort);
    }

    // -- recordFileEvent tests --

    @Test
    void givenCreationEvent_WhenRecordFileEvent_ThenMetricsRecordedAndEmittedPublished() {
        useCase.recordFileEvent("agent-1", ScannerEventType.CREATION, 5L, "/tmp/watch");

        verify(metricsPort).recordEvent("agent-1", ScannerEventType.CREATION, 5L);
        verify(eventPort).publish(eq("agent-1"), eq(ScannerFileResult.EMITTED),
                eq("/tmp/watch"), isNull(String.class));
    }

    @Test
    void givenModificationEvent_WhenRecordFileEvent_ThenMetricsRecordedAndEmittedPublished() {
        useCase.recordFileEvent("agent-1", ScannerEventType.MODIFICATION, 10L, "/tmp/watch");

        verify(metricsPort).recordEvent("agent-1", ScannerEventType.MODIFICATION, 10L);
        verify(eventPort).publish(eq("agent-1"), eq(ScannerFileResult.EMITTED),
                eq("/tmp/watch"), isNull(String.class));
    }

    @Test
    void givenDeletionEvent_WhenRecordFileEvent_ThenMetricsRecordedAndEmittedPublished() {
        useCase.recordFileEvent("agent-1", ScannerEventType.DELETION, 3L, "/tmp/watch");

        verify(metricsPort).recordEvent("agent-1", ScannerEventType.DELETION, 3L);
        verify(eventPort).publish(eq("agent-1"), eq(ScannerFileResult.EMITTED),
                eq("/tmp/watch"), isNull(String.class));
    }

    @Test
    void givenUnchangedEvent_WhenRecordFileEvent_ThenMetricsRecordedAndFilteredPublished() {
        useCase.recordFileEvent("agent-1", ScannerEventType.UNCHANGED, 8L, "/tmp/watch");

        verify(metricsPort).recordEvent("agent-1", ScannerEventType.UNCHANGED, 8L);
        verify(eventPort).publish(eq("agent-1"), eq(ScannerFileResult.FILTERED),
                eq("/tmp/watch"), isNull(String.class));
    }

    @Test
    void givenNullEventType_WhenRecordFileEvent_ThenMetricsRecordedAndEmittedPublished() {
        useCase.recordFileEvent("agent-1", null, 0L, "/tmp/watch");

        verify(metricsPort).recordEvent(eq("agent-1"), isNull(ScannerEventType.class), eq(0L));
        verify(eventPort).publish(eq("agent-1"), eq(ScannerFileResult.EMITTED),
                eq("/tmp/watch"), isNull(String.class));
    }

    @Test
    void givenNullFolderPath_WhenRecordFileEvent_ThenNullFolderPathPublished() {
        useCase.recordFileEvent("agent-1", ScannerEventType.CREATION, 0L, null);

        verify(metricsPort).recordEvent("agent-1", ScannerEventType.CREATION, 0L);
        verify(eventPort).publish(eq("agent-1"), eq(ScannerFileResult.EMITTED),
                isNull(String.class), isNull(String.class));
    }

    // -- recordEmission tests --

    @Test
    void whenRecordEmission_ThenMetricsRecordedAndEmittedPublished() {
        useCase.recordEmission("agent-1", "/tmp/watch");

        verify(metricsPort).recordEmission("agent-1");
        verify(eventPort).publish(eq("agent-1"), eq(ScannerFileResult.EMITTED),
                eq("/tmp/watch"), isNull(String.class));
    }

    @Test
    void whenRecordEmissionWithNullFolder_ThenNullFolderPathPublished() {
        useCase.recordEmission("agent-1", null);

        verify(metricsPort).recordEmission("agent-1");
        verify(eventPort).publish(eq("agent-1"), eq(ScannerFileResult.EMITTED),
                isNull(String.class), isNull(String.class));
    }

    // -- getMetrics tests --

    @Test
    void whenGetMetrics_ThenDelegatesToMetricsPort() {
        ScannerMetrics expected = new ScannerMetrics("agent-1", 5, null, 10);
        when(metricsPort.getMetrics("agent-1")).thenReturn(expected);

        ScannerMetrics result = useCase.getMetrics("agent-1");

        assertThat(result).isEqualTo(expected);
        verify(metricsPort).getMetrics("agent-1");
    }

    @Test
    void whenGetMetrics_NoEventPortInteraction() {
        when(metricsPort.getMetrics("agent-1")).thenReturn(
                new ScannerMetrics("agent-1", 0, null, 0));

        useCase.getMetrics("agent-1");

        verifyNoMoreInteractions(eventPort);
    }

    // -- transitionToError tests --

    @Test
    void whenTransitionToError_ThenMetricsRecordedWithErrorPublished() {
        useCase.transitionToError("agent-1", "disk failure");

        verify(metricsPort).recordEvent(eq("agent-1"), isNull(ScannerEventType.class), eq(0L));
        verify(eventPort).publish(eq("agent-1"), eq(ScannerFileResult.ERROR),
                isNull(String.class), eq("disk failure"));
    }

    @Test
    void givenNullMessage_WhenTransitionToError_ThenNullErrorMessagePublished() {
        useCase.transitionToError("agent-1", null);

        verify(metricsPort).recordEvent(eq("agent-1"), isNull(ScannerEventType.class), eq(0L));
        verify(eventPort).publish(eq("agent-1"), eq(ScannerFileResult.ERROR),
                isNull(String.class), isNull(String.class));
    }

    // -- integration-style test with real event bus --

    @Test
    void givenRealEventBus_WhenRecordFileEvent_ThenCallbackReceivesCorrectEvent() {
        ScannerEventBus realEventBus = new ScannerEventBus();
        useCase = new ScannerObservabilityUseCase(metricsPort, realEventBus);

        AtomicReference<ScannerFileEvent> receivedEvent = new AtomicReference<>();
        realEventBus.registerCallback(receivedEvent::set);

        useCase.recordFileEvent("agent-1", ScannerEventType.CREATION, 5L, "/tmp/watch");

        assertThat(receivedEvent.get()).isNotNull();
        assertThat(receivedEvent.get().agentId()).isEqualTo("agent-1");
        assertThat(receivedEvent.get().result()).isEqualTo(ScannerFileResult.EMITTED);
        assertThat(receivedEvent.get().folderPath()).isEqualTo("/tmp/watch");
    }

    @Test
    void givenRealEventBus_WhenTransitionToError_ThenCallbackReceivesErrorEvent() {
        ScannerEventBus realEventBus = new ScannerEventBus();
        useCase = new ScannerObservabilityUseCase(metricsPort, realEventBus);

        AtomicReference<ScannerFileEvent> receivedEvent = new AtomicReference<>();
        realEventBus.registerCallback(receivedEvent::set);

        useCase.transitionToError("agent-1", "watcher crashed");

        assertThat(receivedEvent.get()).isNotNull();
        assertThat(receivedEvent.get().agentId()).isEqualTo("agent-1");
        assertThat(receivedEvent.get().result()).isEqualTo(ScannerFileResult.ERROR);
        assertThat(receivedEvent.get().errorMessage()).isEqualTo("watcher crashed");
    }

    @Test
    void givenRealEventBus_WhenRecordEmission_ThenCallbackReceivesEmittedEvent() {
        ScannerEventBus realEventBus = new ScannerEventBus();
        useCase = new ScannerObservabilityUseCase(metricsPort, realEventBus);

        AtomicReference<ScannerFileEvent> receivedEvent = new AtomicReference<>();
        realEventBus.registerCallback(receivedEvent::set);

        useCase.recordEmission("agent-1", "/tmp/watch");

        assertThat(receivedEvent.get()).isNotNull();
        assertThat(receivedEvent.get().agentId()).isEqualTo("agent-1");
        assertThat(receivedEvent.get().result()).isEqualTo(ScannerFileResult.EMITTED);
    }

    // -- getAllMetrics tests --

    @Test
    void whenGetAllMetrics_ThenDelegatesToMetricsPort() {
        List<ScannerMetrics> expected = List.of(
                new ScannerMetrics("a", 1, null, 5),
                new ScannerMetrics("b", 3, null, 10)
        );
        when(metricsPort.getAllMetrics()).thenReturn(expected);

        List<ScannerMetrics> result = useCase.getAllMetrics();

        assertThat(result).isEqualTo(expected);
        verify(metricsPort).getAllMetrics();
    }

    // -- isIdle tests --

    @Test
    void whenIsIdle_ThenDelegatesToMetricsPort() {
        when(metricsPort.isIdle("agent-1")).thenReturn(true);

        assertThat(useCase.isIdle("agent-1")).isTrue();
        verify(metricsPort).isIdle("agent-1");
    }

    @Test
    void whenIsIdle_NotIdle_ThenDelegatesToMetricsPort() {
        when(metricsPort.isIdle("agent-1")).thenReturn(false);

        assertThat(useCase.isIdle("agent-1")).isFalse();
        verify(metricsPort).isIdle("agent-1");
    }

    // -- multiple calls tests --

    @Test
    void givenMultipleEvents_WhenRecorded_ThenBothPortsCalledEachTime() {
        useCase.recordFileEvent("agent-1", ScannerEventType.CREATION, 1L, "/tmp");
        useCase.recordFileEvent("agent-1", ScannerEventType.MODIFICATION, 2L, "/tmp");
        useCase.recordFileEvent("agent-1", ScannerEventType.UNCHANGED, 3L, "/tmp");

        verify(metricsPort, times(3))
                .recordEvent(any(String.class), any(ScannerEventType.class), anyLong());
        verify(eventPort, times(3))
                .publish(any(String.class), any(ScannerFileResult.class),
                        any(String.class), isNull(String.class));
    }
}

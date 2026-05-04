package com.hdekker.ai_workflow.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import com.hdekker.ai_workflow.adapter.inbound.rest.dto.ScannerMetricsSnapshot;
import com.hdekker.ai_workflow.adapter.inbound.ui.event.ScannerMetricsChangedEvent;
import com.hdekker.ai_workflow.domain.scanner.ScannerEventType;
import com.hdekker.ai_workflow.domain.scanner.ScannerStatus;

import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link ScannerObserverUseCase}.
 */
@ExtendWith(MockitoExtension.class)
public class ScannerObserverUseCaseTest {

    private ScannerObserverUseCase useCase;

    @Mock
    private FileCounter fileCounter;

    @BeforeEach
    void setUp() {
        useCase = new ScannerObserverUseCase(fileCounter);
    }

    // -- Parameterised callback test --

    record CallbackScenario(
            String name,
            ScannerMetricsChangedEvent event,
            int expectedCallbackCount,
            String expectedType,
            ScannerStatus expectedStatus
    ) {}

    private static Stream<CallbackScenario> callbackScenarios() {
        return Stream.of(
                new CallbackScenario(
                        "creation event triggers callback",
                        callbackEvent(ScannerEventType.CREATION),
                        1, "creation", null
                ),
                new CallbackScenario(
                        "deletion event triggers callback",
                        callbackEvent(ScannerEventType.DELETION),
                        1, "deletion", null
                ),
                new CallbackScenario(
                        "unchanged event triggers callback",
                        callbackEvent(ScannerEventType.UNCHANGED),
                        1, "unchanged", null
                ),
                new CallbackScenario(
                        "emission event triggers callback",
                        new ScannerMetricsChangedEvent("agent-1", ScannerStatus.EMITTING_UPDATES, null, null, null),
                        1, "emitting_updates", null
                ),
                new CallbackScenario(
                        "error event triggers callback",
                        new ScannerMetricsChangedEvent("agent-1", ScannerStatus.ERROR, null, null, "some error"),
                        1, "error", ScannerStatus.ERROR
                ),
                new CallbackScenario(
                        "idle event triggers callback",
                        new ScannerMetricsChangedEvent("agent-1", ScannerStatus.IDLE, null, null, null),
                        1, "idle", ScannerStatus.IDLE
                )
        );
    }

    private static ScannerMetricsChangedEvent callbackEvent(ScannerEventType type) {
        ScannerStatus status = type == ScannerEventType.UNCHANGED
                ? ScannerStatus.FILTERED : ScannerStatus.EMITTING_UPDATES;
        return new ScannerMetricsChangedEvent("agent-1", status, type, "/tmp/test", null);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("callbackScenarios")
    void givenCallbackRegistered_WhenEventRecorded_ThenCallbackReceivesEvent(CallbackScenario scenario) {
        CopyOnWriteArrayList<ScannerMetricsChangedEvent> events = new CopyOnWriteArrayList<>();
        useCase.registerRefreshCallback(events::add);

        useCase.recordScannerEvent(scenario.event());

        assertThat(events).hasSize(scenario.expectedCallbackCount());
        assertThat(events.get(0).getAgentId()).isEqualTo("agent-1");
        assertThat(events.get(0).getType()).isEqualTo(scenario.expectedType());
        if (scenario.expectedStatus() != null) {
            assertThat(events.get(0).getStatus()).isEqualTo(scenario.expectedStatus());
        }
    }

    @Test
    void givenMultipleCallbacks_WhenEventRecorded_ThenAllInvoked() {
        AtomicInteger count1 = new AtomicInteger();
        AtomicInteger count2 = new AtomicInteger();

        useCase.registerRefreshCallback(e -> count1.incrementAndGet());
        useCase.registerRefreshCallback(e -> count2.incrementAndGet());

        useCase.recordScannerEvent(event(ScannerEventType.CREATION));

        assertThat(count1).hasValue(1);
        assertThat(count2).hasValue(1);
    }

    @Test
    void givenCallbackUnregistered_WhenEventRecorded_ThenCallbackNotInvoked() {
        AtomicInteger count = new AtomicInteger();
        java.util.function.Consumer<ScannerMetricsChangedEvent> callback = e -> count.incrementAndGet();
        useCase.registerRefreshCallback(callback);
        useCase.unregisterRefreshCallback(callback);

        useCase.recordScannerEvent(event(ScannerEventType.CREATION));

        assertThat(count).hasValue(0);
    }

    @Test
    void givenCallbackThrows_WhenEventRecorded_ThenOtherCallbacksStillInvoked() {
        CopyOnWriteArrayList<ScannerMetricsChangedEvent> goodEvents = new CopyOnWriteArrayList<>();

        useCase.registerRefreshCallback(e -> {
            throw new RuntimeException("callback error");
        });
        useCase.registerRefreshCallback(goodEvents::add);

        useCase.recordScannerEvent(event(ScannerEventType.CREATION));

        assertThat(goodEvents).hasSize(1);
    }

    @Test
    void givenNoCallbacksRegistered_WhenEventRecorded_ThenNoException() {
        useCase.recordScannerEvent(event(ScannerEventType.CREATION));
        useCase.recordScannerEvent(event(ScannerEventType.DELETION));
        useCase.recordScannerEvent(
                new ScannerMetricsChangedEvent("agent-1", ScannerStatus.EMITTING_UPDATES, null, null, null));
    }

    // -- Parameterised file count test --

    record CountScenario(
            String name,
            List<ScannerMetricsChangedEvent> events,
            String agentId,
            long expectedDiscovered,
            long expectedFileCount
    ) {}

    private static Stream<CountScenario> countScenarios() {
        return Stream.of(
                new CountScenario(
                        "no metrics returns zero values",
                        List.of(),
                        "agent-1", 0, 0
                ),
                new CountScenario(
                        "creation increments discovered",
                        List.of(countEvent("agent-1", ScannerEventType.CREATION)),
                        "agent-1", 1, 42
                ),
                new CountScenario(
                        "modification increments discovered",
                        List.of(countEvent("agent-1", ScannerEventType.MODIFICATION)),
                        "agent-1", 1, 42
                ),
                new CountScenario(
                        "deletion does not increment discovered",
                        List.of(countEvent("agent-1", ScannerEventType.DELETION)),
                        "agent-1", 0, 42
                ),
                new CountScenario(
                        "unchanged does not increment discovered",
                        List.of(countEvent("agent-1", ScannerEventType.UNCHANGED)),
                        "agent-1", 0, 42
                ),
                new CountScenario(
                        "multiple creations accumulate",
                        List.of(
                                countEvent("agent-1", ScannerEventType.CREATION),
                                countEvent("agent-1", ScannerEventType.CREATION),
                                countEvent("agent-1", ScannerEventType.CREATION)
                        ),
                        "agent-1", 3, 42
                ),
                new CountScenario(
                        "mixed events count only creations and modifications",
                        List.of(
                                countEvent("agent-1", ScannerEventType.CREATION),
                                countEvent("agent-1", ScannerEventType.MODIFICATION),
                                countEvent("agent-1", ScannerEventType.DELETION),
                                countEvent("agent-1", ScannerEventType.UNCHANGED)
                        ),
                        "agent-1", 2, 42
                ),
                new CountScenario(
                        "multiple agents return correct values per agent",
                        List.of(
                                countEvent("agent-a", ScannerEventType.CREATION),
                                countEvent("agent-a", ScannerEventType.CREATION),
                                countEvent("agent-b", ScannerEventType.MODIFICATION),
                                countEvent("agent-b", ScannerEventType.DELETION)
                        ),
                        "agent-a", 2, 42
                )
        );
    }

    private static ScannerMetricsChangedEvent countEvent(String agentId, ScannerEventType type) {
        ScannerStatus status = type == ScannerEventType.UNCHANGED
                ? ScannerStatus.FILTERED : ScannerStatus.EMITTING_UPDATES;
        return new ScannerMetricsChangedEvent(agentId, status, type, "/tmp/" + agentId, null);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("countScenarios")
    void givenEventsRecorded_WhenGettingMetrics_ThenCountsMatch(CountScenario scenario) {
        for (ScannerMetricsChangedEvent e : scenario.events()) {
            useCase.recordScannerEvent(e);
        }

        if (!scenario.events().isEmpty()) {
            when(fileCounter.countFiles("/tmp/" + scenario.agentId())).thenReturn(scenario.expectedFileCount());
        }

        ScannerMetricsSnapshot snapshot = useCase.getMetrics(scenario.agentId());
        assertThat(snapshot.agentId()).isEqualTo(scenario.agentId());
        assertThat(snapshot.totalDiscovered()).isEqualTo(scenario.expectedDiscovered());
        assertThat(snapshot.fileCount()).isEqualTo(scenario.expectedFileCount());
    }

    @Test
    void givenNoMetrics_WhenGetAllMetrics_ThenReturnsEmptyList() {
        assertThat(useCase.getAllMetrics()).isEmpty();
    }

    @Test
    void givenMultipleAgents_WhenGetAllMetrics_ThenReturnsAllSnapshots() {
        useCase.recordScannerEvent(event(ScannerEventType.CREATION, "agent-a"));
        useCase.recordScannerEvent(event(ScannerEventType.CREATION, "agent-b"));

        List<ScannerMetricsSnapshot> all = useCase.getAllMetrics();

        assertThat(all).hasSize(2);
        assertThat(all).extracting(ScannerMetricsSnapshot::agentId)
                .containsExactlyInAnyOrder("agent-a", "agent-b");
    }

    @Test
    void givenEmissionRecorded_ThenIdleBecomesFalse() {
        useCase.recordScannerEvent(event(ScannerEventType.CREATION, "agent-1"));
        useCase.recordScannerEvent(new ScannerMetricsChangedEvent("agent-1", ScannerStatus.EMITTING_UPDATES, null, null, null));

        assertThat(useCase.isIdle("agent-1")).isFalse();
    }

    @Test
    void givenNoEmission_WhenIsIdle_ThenReturnsTrue() {
        useCase.recordScannerEvent(event(ScannerEventType.CREATION, "agent-1"));

        assertThat(useCase.isIdle("agent-1")).isTrue();
    }

    @Test
    void givenNoEvents_WhenIsIdle_ThenReturnsTrue() {
        assertThat(useCase.isIdle("agent-1")).isTrue();
    }

    @Test
    void givenConcurrentAccess_WhenMultipleThreadsUpdate_ThenMetricsConsistent() throws InterruptedException {
        String agentId = "concurrent-agent";
        int threadCount = 10;
        int updatesPerThread = 100;

        Thread[] threads = new Thread[threadCount];
        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < updatesPerThread; j++) {
                    if (j % 3 == 0) {
                        useCase.recordScannerEvent(new ScannerMetricsChangedEvent(agentId, ScannerStatus.EMITTING_UPDATES, ScannerEventType.CREATION, "/tmp/test", null));
                    } else if (j % 3 == 1) {
                        useCase.recordScannerEvent(new ScannerMetricsChangedEvent(agentId, ScannerStatus.FILTERED, ScannerEventType.UNCHANGED, "/tmp/test", null));
                    } else {
                        useCase.recordScannerEvent(new ScannerMetricsChangedEvent(agentId, ScannerStatus.EMITTING_UPDATES, null, null, null));
                    }
                }
            });
            threads[i].start();
        }

        for (Thread t : threads) {
            t.join(10000);
        }

        assertThat(useCase.getMetrics(agentId).totalDiscovered()).isGreaterThan(0);
    }

    // -- Helpers --

    private static ScannerMetricsChangedEvent event(ScannerEventType type) {
        return event(type, "agent-1");
    }

    private static ScannerMetricsChangedEvent event(ScannerEventType type, String agentId) {
        ScannerStatus status = type == ScannerEventType.UNCHANGED
                ? ScannerStatus.FILTERED : ScannerStatus.EMITTING_UPDATES;
        return new ScannerMetricsChangedEvent(agentId, status, type, "/tmp/test", null);
    }
}

package com.hdekker.ai_workflow.application.scanner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import com.hdekker.ai_workflow.application.file.port.FileCounterPort;
import com.hdekker.ai_workflow.domain.scanner.ScannerEventType;
import com.hdekker.ai_workflow.domain.scanner.ScannerMetrics;
import com.hdekker.ai_workflow.domain.scanner.ScannerMetricsEvent;
import com.hdekker.ai_workflow.domain.scanner.ScannerStatus;

import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Unit tests for {@link ScannerObserverService}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class ScannerObserverServiceTest {

    private ScannerObserverService useCase;

    @Mock
    private FileCounterPort fileCounter;

    @BeforeEach
    void setUp() {
        useCase = new ScannerObserverService(fileCounter);
    }

    // -- Parameterised callback test --

    record CallbackScenario(
            String name,
            ScannerEventType eventType,
            ScannerStatus status,
            int expectedCallbackCount,
            ScannerEventType expectedType,
            ScannerStatus expectedStatus
    ) {}

    private static Stream<CallbackScenario> callbackScenarios() {
        return Stream.of(
                new CallbackScenario("creation event triggers callback",
                        ScannerEventType.CREATION, ScannerStatus.EMITTING_UPDATES,
                        1, ScannerEventType.CREATION, ScannerStatus.EMITTING_UPDATES),
                new CallbackScenario("deletion event triggers callback",
                        ScannerEventType.DELETION, ScannerStatus.EMITTING_UPDATES,
                        1, ScannerEventType.DELETION, ScannerStatus.EMITTING_UPDATES),
                new CallbackScenario("unchanged event triggers callback",
                        ScannerEventType.UNCHANGED, ScannerStatus.FILTERED,
                        1, ScannerEventType.UNCHANGED, ScannerStatus.FILTERED)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("callbackScenarios")
    void givenCallbackRegistered_WhenEventRecorded_ThenCallbackReceivesEvent(CallbackScenario scenario) {
        CopyOnWriteArrayList<ScannerMetricsEvent> events = new CopyOnWriteArrayList<>();
        useCase.registerRefreshCallback(events::add);

        useCase.recordEvent("agent-1", scenario.eventType(), scenario.status(), "/tmp/test", null);

        assertThat(events).hasSize(scenario.expectedCallbackCount());
        assertThat(events.get(0).agentId()).isEqualTo("agent-1");
        assertThat(events.get(0).eventType()).isEqualTo(scenario.expectedType());
        assertThat(events.get(0).status()).isEqualTo(scenario.expectedStatus());
    }

    @Test
    void givenStatusPush_WhenCallbackRegistered_ThenCallbackReceivesEvent() {
        CopyOnWriteArrayList<ScannerMetricsEvent> events = new CopyOnWriteArrayList<>();
        useCase.registerRefreshCallback(events::add);

        useCase.pushToUI("agent-1", ScannerStatus.EMITTING_UPDATES);

        assertThat(events).hasSize(1);
        assertThat(events.get(0).agentId()).isEqualTo("agent-1");
        assertThat(events.get(0).status()).isEqualTo(ScannerStatus.EMITTING_UPDATES);
        // pushToUI with just status creates events with null eventType
        assertThat(events.get(0).eventType()).isNull();
    }

    @Test
    void givenMultipleCallbacks_WhenEventRecorded_ThenAllInvoked() {
        AtomicInteger count1 = new AtomicInteger();
        AtomicInteger count2 = new AtomicInteger();

        useCase.registerRefreshCallback(e -> count1.incrementAndGet());
        useCase.registerRefreshCallback(e -> count2.incrementAndGet());

        useCase.recordEvent("agent-1", ScannerEventType.CREATION, ScannerStatus.EMITTING_UPDATES, "/tmp/test", null);

        assertThat(count1).hasValue(1);
        assertThat(count2).hasValue(1);
    }

    @Test
    void givenCallbackUnregistered_WhenEventRecorded_ThenCallbackNotInvoked() {
        AtomicInteger count = new AtomicInteger();
        java.util.function.Consumer<ScannerMetricsEvent> callback = e -> count.incrementAndGet();
        useCase.registerRefreshCallback(callback);
        useCase.unregisterRefreshCallback(callback);

        useCase.recordEvent("agent-1", ScannerEventType.CREATION, ScannerStatus.EMITTING_UPDATES, "/tmp/test", null);

        assertThat(count).hasValue(0);
    }

    @Test
    void givenCallbackThrows_WhenEventRecorded_ThenOtherCallbacksStillInvoked() {
        CopyOnWriteArrayList<ScannerMetricsEvent> goodEvents = new CopyOnWriteArrayList<>();

        useCase.registerRefreshCallback(e -> {
            throw new RuntimeException("callback error");
        });
        useCase.registerRefreshCallback(goodEvents::add);

        useCase.recordEvent("agent-1", ScannerEventType.CREATION, ScannerStatus.EMITTING_UPDATES, "/tmp/test", null);

        assertThat(goodEvents).hasSize(1);
    }

    @Test
    void givenNoCallbacksRegistered_WhenEventRecorded_ThenNoException() {
        useCase.recordEvent("agent-1", ScannerEventType.CREATION, ScannerStatus.EMITTING_UPDATES, "/tmp/test", null);
        useCase.recordEvent("agent-1", ScannerEventType.DELETION, ScannerStatus.EMITTING_UPDATES, "/tmp/test", null);
        useCase.pushToUI("agent-1", ScannerStatus.ERROR);
    }

    // -- Metrics counting tests --

    @Test
    void givenNoEvents_WhenGettingMetrics_ThenReturnsEmptyMetrics() {
        ScannerMetrics metrics = useCase.getMetrics("agent-1");
        assertThat(metrics.agentId()).isEqualTo("agent-1");
        assertThat(metrics.totalDiscovered()).isZero();
        assertThat(metrics.lastEmissionTimestamp()).isNull();
    }

    @Test
    void givenCreationEvent_WhenGettingMetrics_ThenDiscoveredIsOne() {
        useCase.recordEvent("agent-1", ScannerEventType.CREATION, ScannerStatus.EMITTING_UPDATES, "/tmp/agent-1", null);

        ScannerMetrics metrics = useCase.getMetrics("agent-1");
        assertThat(metrics.totalDiscovered()).isEqualTo(1);
    }

    @Test
    void givenModificationEvent_WhenGettingMetrics_ThenDiscoveredIsOne() {
        useCase.recordEvent("agent-1", ScannerEventType.MODIFICATION, ScannerStatus.EMITTING_UPDATES, "/tmp/agent-1", null);

        ScannerMetrics metrics = useCase.getMetrics("agent-1");
        assertThat(metrics.totalDiscovered()).isEqualTo(1);
    }

    @Test
    void givenDeletionEvent_WhenGettingMetrics_ThenDiscoveredIsZero() {
        useCase.recordEvent("agent-1", ScannerEventType.DELETION, ScannerStatus.EMITTING_UPDATES, "/tmp/agent-1", null);

        ScannerMetrics metrics = useCase.getMetrics("agent-1");
        assertThat(metrics.totalDiscovered()).isZero();
    }

    @Test
    void givenUnchangedEvent_WhenGettingMetrics_ThenDiscoveredIsZero() {
        useCase.recordEvent("agent-1", ScannerEventType.UNCHANGED, ScannerStatus.FILTERED, "/tmp/agent-1", null);

        ScannerMetrics metrics = useCase.getMetrics("agent-1");
        assertThat(metrics.totalDiscovered()).isZero();
    }

    @Test
    void givenMultipleCreations_WhenGettingMetrics_ThenAccumulates() {
        useCase.recordEvent("agent-1", ScannerEventType.CREATION, ScannerStatus.EMITTING_UPDATES, "/tmp/agent-1", null);
        useCase.recordEvent("agent-1", ScannerEventType.CREATION, ScannerStatus.EMITTING_UPDATES, "/tmp/agent-1", null);
        useCase.recordEvent("agent-1", ScannerEventType.CREATION, ScannerStatus.EMITTING_UPDATES, "/tmp/agent-1", null);

        ScannerMetrics metrics = useCase.getMetrics("agent-1");
        assertThat(metrics.totalDiscovered()).isEqualTo(3);
    }

    @Test
    void givenMixedEvents_WhenGettingMetrics_ThenCountsOnlyCreationAndModification() {
        useCase.recordEvent("agent-1", ScannerEventType.CREATION, ScannerStatus.EMITTING_UPDATES, "/tmp/agent-1", null);
        useCase.recordEvent("agent-1", ScannerEventType.MODIFICATION, ScannerStatus.EMITTING_UPDATES, "/tmp/agent-1", null);
        useCase.recordEvent("agent-1", ScannerEventType.DELETION, ScannerStatus.EMITTING_UPDATES, "/tmp/agent-1", null);
        useCase.recordEvent("agent-1", ScannerEventType.UNCHANGED, ScannerStatus.FILTERED, "/tmp/agent-1", null);

        ScannerMetrics metrics = useCase.getMetrics("agent-1");
        assertThat(metrics.totalDiscovered()).isEqualTo(2);
    }

    @Test
    void givenMultipleAgents_WhenGettingMetrics_ThenReturnsCorrectPerAgent() {
        useCase.recordEvent("agent-a", ScannerEventType.CREATION, ScannerStatus.EMITTING_UPDATES, "/tmp/agent-a", null);
        useCase.recordEvent("agent-a", ScannerEventType.CREATION, ScannerStatus.EMITTING_UPDATES, "/tmp/agent-a", null);
        useCase.recordEvent("agent-b", ScannerEventType.MODIFICATION, ScannerStatus.EMITTING_UPDATES, "/tmp/agent-b", null);
        useCase.recordEvent("agent-b", ScannerEventType.DELETION, ScannerStatus.EMITTING_UPDATES, "/tmp/agent-b", null);

        assertThat(useCase.getMetrics("agent-a").totalDiscovered()).isEqualTo(2);
        assertThat(useCase.getMetrics("agent-b").totalDiscovered()).isEqualTo(1);
    }

    @Test
    void givenNoMetrics_WhenGetAllMetrics_ThenReturnsEmptyList() {
        assertThat(useCase.getAllMetrics()).isEmpty();
    }

    @Test
    void givenMultipleAgents_WhenGetAllMetrics_ThenReturnsAllSnapshots() {
        useCase.recordEvent("agent-a", ScannerEventType.CREATION, ScannerStatus.EMITTING_UPDATES, "/tmp/agent-a", null);
        useCase.recordEvent("agent-b", ScannerEventType.CREATION, ScannerStatus.EMITTING_UPDATES, "/tmp/agent-b", null);

        List<ScannerMetrics> all = useCase.getAllMetrics();

        assertThat(all).hasSize(2);
        assertThat(all).extracting(ScannerMetrics::agentId)
                .containsExactlyInAnyOrder("agent-a", "agent-b");
    }

    @Test
    void givenEmissionRecorded_ThenIdleBecomesFalse() {
        useCase.recordEvent("agent-1", ScannerEventType.CREATION, ScannerStatus.EMITTING_UPDATES, "/tmp/test", null);
        useCase.recordEmission("agent-1");

        assertThat(useCase.isIdle("agent-1")).isFalse();
    }

    @Test
    void givenNoEmission_WhenIsIdle_ThenReturnsTrue() {
        useCase.recordEvent("agent-1", ScannerEventType.CREATION, ScannerStatus.EMITTING_UPDATES, "/tmp/test", null);

        assertThat(useCase.isIdle("agent-1")).isTrue();
    }

    @Test
    void givenNoEvents_WhenIsIdle_ThenReturnsTrue() {
        assertThat(useCase.isIdle("agent-1")).isTrue();
    }

    // -- countFiles() tests --

    @Test
    void givenAgentWithFolder_WhenCountFilesCalled_ThenReturnsMockedCount() {
        String agentId = "count-agent";
        String folderPath = "/tmp/count-test";

        // Store a folder, then verify countFiles delegates to the mock
        useCase.storeFolder(agentId, folderPath);
        when(fileCounter.countFiles(folderPath)).thenReturn(7L);

        long count = useCase.countFiles(agentId);
        assertThat(count).isEqualTo(7L);
    }

    @Test
    void givenAgentWithoutFolder_WhenCountFilesCalled_ThenReturnsZero() {
        // No folder stored — should return 0 regardless of the file counter
        long count = useCase.countFiles("nonexistent-agent");
        assertThat(count).isZero();
    }

    @Test
    void givenFileCounterThrows_WhenCountFilesCalled_ThenReturnsZero() {
        String agentId = "error-agent";
        String folderPath = "/tmp/error-test";

        useCase.storeFolder(agentId, folderPath);
        doThrow(new RuntimeException("disk full")).when(fileCounter).countFiles(folderPath);

        // countFiles should handle the exception gracefully
        long count = useCase.countFiles(agentId);
        assertThat(count).isZero();
    }

    @Test
    void givenConcurrentAccess_WhenMultipleThreadsUpdate_ThenMetricsConsistent() throws InterruptedException {
        String agentId = "concurrent-agent";
        String folderPath = "/tmp/test";
        int threadCount = 10;
        int updatesPerThread = 100;

        Thread[] threads = new Thread[threadCount];
        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < updatesPerThread; j++) {
                    if (j % 3 == 0) {
                        useCase.recordEvent(agentId, ScannerEventType.CREATION, ScannerStatus.EMITTING_UPDATES, folderPath, null);
                    } else if (j % 3 == 1) {
                        useCase.recordEvent(agentId, ScannerEventType.UNCHANGED, ScannerStatus.FILTERED, folderPath, null);
                    } else {
                        useCase.recordEmission(agentId);
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
}

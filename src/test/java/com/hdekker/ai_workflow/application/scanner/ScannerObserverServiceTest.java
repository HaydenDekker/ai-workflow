package com.hdekker.ai_workflow.application.scanner;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import com.hdekker.ai_workflow.domain.scanner.ScannerEventType;
import com.hdekker.ai_workflow.domain.scanner.ScannerFileEvent;
import com.hdekker.ai_workflow.domain.scanner.ScannerFileResult;
import com.hdekker.ai_workflow.domain.scanner.ScannerMetrics;
import com.hdekker.ai_workflow.domain.scanner.ScannerMetricsEvent;
import com.hdekker.ai_workflow.domain.scanner.ScannerStatus;

import org.junit.jupiter.api.extension.ExtendWith;
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

    @BeforeEach
    void setUp() {
        useCase = new ScannerObserverService();
    }

    // -- Parameterised callback test --

    record CallbackScenario(
            String name,
            ScannerEventType eventType,
            int expectedCallbackCount,
            ScannerEventType expectedType
    ) {}

    private static Stream<CallbackScenario> callbackScenarios() {
        return Stream.of(
                new CallbackScenario("creation event triggers callback",
                        ScannerEventType.CREATION, 1, ScannerEventType.CREATION),
                new CallbackScenario("deletion event triggers callback",
                        ScannerEventType.DELETION, 1, ScannerEventType.DELETION),
                new CallbackScenario("unchanged event triggers callback",
                        ScannerEventType.UNCHANGED, 1, ScannerEventType.UNCHANGED)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("callbackScenarios")
    void givenCallbackRegistered_WhenEventRecorded_ThenCallbackReceivesEvent(CallbackScenario scenario) {
        CopyOnWriteArrayList<ScannerMetricsEvent> events = new CopyOnWriteArrayList<>();
        useCase.registerRefreshCallback(events::add);

        useCase.recordEvent("agent-1", scenario.eventType(), 0L);

        assertThat(events).hasSize(scenario.expectedCallbackCount());
        assertThat(events.get(0).agentId()).isEqualTo("agent-1");
        assertThat(events.get(0).eventType()).isEqualTo(scenario.expectedType());
        // Callbacks receive events pushed internally — status is null from the port
        // (status was removed from recordEvent; it's now a UI/push concern)
        assertThat(events.get(0).status()).isNull();
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

        useCase.recordEvent("agent-1", ScannerEventType.CREATION, 0L);

        assertThat(count1).hasValue(1);
        assertThat(count2).hasValue(1);
    }

    @Test
    void givenCallbackUnregistered_WhenEventRecorded_ThenCallbackNotInvoked() {
        AtomicInteger count = new AtomicInteger();
        java.util.function.Consumer<ScannerMetricsEvent> callback = e -> count.incrementAndGet();
        useCase.registerRefreshCallback(callback);
        useCase.unregisterRefreshCallback(callback);

        useCase.recordEvent("agent-1", ScannerEventType.CREATION, 0L);

        assertThat(count).hasValue(0);
    }

    @Test
    void givenCallbackThrows_WhenEventRecorded_ThenOtherCallbacksStillInvoked() {
        CopyOnWriteArrayList<ScannerMetricsEvent> goodEvents = new CopyOnWriteArrayList<>();

        useCase.registerRefreshCallback(e -> {
            throw new RuntimeException("callback error");
        });
        useCase.registerRefreshCallback(goodEvents::add);

        useCase.recordEvent("agent-1", ScannerEventType.CREATION, 0L);

        assertThat(goodEvents).hasSize(1);
    }

    @Test
    void givenNoCallbacksRegistered_WhenEventRecorded_ThenNoException() {
        useCase.recordEvent("agent-1", ScannerEventType.CREATION, 0L);
        useCase.recordEvent("agent-1", ScannerEventType.DELETION, 0L);
        useCase.pushToUI("agent-1", ScannerStatus.ERROR);
    }

    // -- Metrics counting tests --

    // -- fileCount domain field tests (Phase 1) --

    @Test
    void givenMetricsCreated_ThenFileCountIsZero() {
        ScannerMetrics metrics = useCase.getMetrics("agent-1");
        assertThat(metrics.fileCount()).isZero();
    }

    @Test
    void givenEventWithFileCount_WhenRecorded_ThenStoredInMetrics() {
        useCase.recordEvent("agent-1", ScannerEventType.CREATION, 42L);

        ScannerMetrics metrics = useCase.getMetrics("agent-1");
        assertThat(metrics.fileCount()).isEqualTo(42L);
    }

    @Test
    void givenMetricsWithFileCount_ThenFileCountReturned() {
        useCase.recordEvent("agent-1", ScannerEventType.CREATION, 100L);
        useCase.recordEvent("agent-1", ScannerEventType.CREATION, 150L);

        ScannerMetrics metrics = useCase.getMetrics("agent-1");
        assertThat(metrics.fileCount()).isEqualTo(150L);
        assertThat(metrics.totalDiscovered()).isEqualTo(2);
    }

    @Test
    void givenEventWithoutFileCount_WhenRecorded_ThenFileCountDefaultsToZero() {
        useCase.recordEvent("agent-1", ScannerEventType.CREATION);

        ScannerMetrics metrics = useCase.getMetrics("agent-1");
        assertThat(metrics.fileCount()).isZero();
    }

    @Test
    void givenEventWithFileCount_WhenPushed_ThenEventContainsFileCount() {
        CopyOnWriteArrayList<ScannerMetricsEvent> events = new CopyOnWriteArrayList<>();
        useCase.registerRefreshCallback(events::add);

        useCase.recordEvent("agent-1", ScannerEventType.CREATION, 77L);

        assertThat(events).hasSize(1);
        assertThat(events.get(0).fileCount()).isEqualTo(77L);
    }

    @Test
    void givenDeletionEvent_WhenRecordedWithFileCount_ThenFileCountStored() {
        useCase.recordEvent("agent-1", ScannerEventType.DELETION, 33L);

        ScannerMetrics metrics = useCase.getMetrics("agent-1");
        assertThat(metrics.fileCount()).isEqualTo(33L);
        assertThat(metrics.totalDiscovered()).isZero();
    }

    @Test
    void givenNoEvents_WhenGettingMetrics_ThenReturnsEmptyMetrics() {
        ScannerMetrics metrics = useCase.getMetrics("agent-1");
        assertThat(metrics.agentId()).isEqualTo("agent-1");
        assertThat(metrics.totalDiscovered()).isZero();
        assertThat(metrics.lastEmissionTimestamp()).isNull();
    }

    @Test
    void givenCreationEvent_WhenGettingMetrics_ThenDiscoveredIsOne() {
        useCase.recordEvent("agent-1", ScannerEventType.CREATION, 0L);

        ScannerMetrics metrics = useCase.getMetrics("agent-1");
        assertThat(metrics.totalDiscovered()).isEqualTo(1);
    }

    @Test
    void givenModificationEvent_WhenGettingMetrics_ThenDiscoveredIsOne() {
        useCase.recordEvent("agent-1", ScannerEventType.MODIFICATION, 0L);

        ScannerMetrics metrics = useCase.getMetrics("agent-1");
        assertThat(metrics.totalDiscovered()).isEqualTo(1);
    }

    @Test
    void givenDeletionEvent_WhenGettingMetrics_ThenDiscoveredIsZero() {
        useCase.recordEvent("agent-1", ScannerEventType.DELETION, 0L);

        ScannerMetrics metrics = useCase.getMetrics("agent-1");
        assertThat(metrics.totalDiscovered()).isZero();
    }

    @Test
    void givenUnchangedEvent_WhenGettingMetrics_ThenDiscoveredIsZero() {
        useCase.recordEvent("agent-1", ScannerEventType.UNCHANGED, 0L);

        ScannerMetrics metrics = useCase.getMetrics("agent-1");
        assertThat(metrics.totalDiscovered()).isZero();
    }

    @Test
    void givenMultipleCreations_WhenGettingMetrics_ThenAccumulates() {
        useCase.recordEvent("agent-1", ScannerEventType.CREATION, 0L);
        useCase.recordEvent("agent-1", ScannerEventType.CREATION, 0L);
        useCase.recordEvent("agent-1", ScannerEventType.CREATION, 0L);

        ScannerMetrics metrics = useCase.getMetrics("agent-1");
        assertThat(metrics.totalDiscovered()).isEqualTo(3);
    }

    @Test
    void givenMixedEvents_WhenGettingMetrics_ThenCountsOnlyCreationAndModification() {
        useCase.recordEvent("agent-1", ScannerEventType.CREATION, 0L);
        useCase.recordEvent("agent-1", ScannerEventType.MODIFICATION, 0L);
        useCase.recordEvent("agent-1", ScannerEventType.DELETION, 0L);
        useCase.recordEvent("agent-1", ScannerEventType.UNCHANGED, 0L);

        ScannerMetrics metrics = useCase.getMetrics("agent-1");
        assertThat(metrics.totalDiscovered()).isEqualTo(2);
    }

    @Test
    void givenMultipleAgents_WhenGettingMetrics_ThenReturnsCorrectPerAgent() {
        useCase.recordEvent("agent-a", ScannerEventType.CREATION, 0L);
        useCase.recordEvent("agent-a", ScannerEventType.CREATION, 0L);
        useCase.recordEvent("agent-b", ScannerEventType.MODIFICATION, 0L);
        useCase.recordEvent("agent-b", ScannerEventType.DELETION, 0L);

        assertThat(useCase.getMetrics("agent-a").totalDiscovered()).isEqualTo(2);
        assertThat(useCase.getMetrics("agent-b").totalDiscovered()).isEqualTo(1);
    }

    @Test
    void givenNoMetrics_WhenGetAllMetrics_ThenReturnsEmptyList() {
        assertThat(useCase.getAllMetrics()).isEmpty();
    }

    @Test
    void givenMultipleAgents_WhenGetAllMetrics_ThenReturnsAllSnapshots() {
        useCase.recordEvent("agent-a", ScannerEventType.CREATION, 0L);
        useCase.recordEvent("agent-b", ScannerEventType.CREATION, 0L);

        List<ScannerMetrics> all = useCase.getAllMetrics();

        assertThat(all).hasSize(2);
        assertThat(all).extracting(ScannerMetrics::agentId)
                .containsExactlyInAnyOrder("agent-a", "agent-b");
    }

    @Test
    void givenEmissionRecorded_ThenIdleBecomesFalse() {
        useCase.recordEvent("agent-1", ScannerEventType.CREATION, 0L);
        useCase.recordEmission("agent-1");

        assertThat(useCase.isIdle("agent-1")).isFalse();
    }

    @Test
    void givenNoEmission_WhenIsIdle_ThenReturnsTrue() {
        useCase.recordEvent("agent-1", ScannerEventType.CREATION, 0L);

        assertThat(useCase.isIdle("agent-1")).isTrue();
    }

    @Test
    void givenNoEvents_WhenIsIdle_ThenReturnsTrue() {
        assertThat(useCase.isIdle("agent-1")).isTrue();
    }

    @Test
    void givenDeletionEvent_WhenPushed_ThenEventContainsFileCount() {
        CopyOnWriteArrayList<ScannerMetricsEvent> events = new CopyOnWriteArrayList<>();
        useCase.registerRefreshCallback(events::add);

        useCase.recordEvent("agent-1", ScannerEventType.DELETION, 5L);

        assertThat(events).hasSize(1);
        assertThat(events.get(0).fileCount()).isEqualTo(5L);
    }

    @Test
    void givenUnchangedEvent_WhenPushed_ThenEventContainsFileCount() {
        CopyOnWriteArrayList<ScannerMetricsEvent> events = new CopyOnWriteArrayList<>();
        useCase.registerRefreshCallback(events::add);

        useCase.recordEvent("agent-1", ScannerEventType.UNCHANGED, 10L);

        assertThat(events).hasSize(1);
        assertThat(events.get(0).fileCount()).isEqualTo(10L);
    }

    @Test
    void givenLifecycleEvent_WhenRecordedWithFileCount_ThenFileCountStored() {
        useCase.recordEvent("agent-1", null, 20L);

        ScannerMetrics metrics = useCase.getMetrics("agent-1");
        assertThat(metrics.fileCount()).isEqualTo(20L);
    }

    // -- fileCount persistence across emission events (regression test) --

    @Test
    void givenFileEventWithCount_WhenEmissionRecorded_ThenFileCountPreserved() {
        // Simulates: file event sets count to 7, then emission callback fires
        useCase.recordEvent("agent-1", ScannerEventType.CREATION, 7L);
        assertThat(useCase.getMetrics("agent-1").fileCount()).isEqualTo(7L);

        // Emission event should NOT reset fileCount to 0
        useCase.recordEmission("agent-1");
        useCase.recordEvent("agent-1", null, 7L);

        assertThat(useCase.getMetrics("agent-1").fileCount()).isEqualTo(7L);
    }

    @Test
    void givenFileEventWithCount_WhenLifecycleEventRecorded_ThenFileCountPreserved() {
        useCase.recordEvent("agent-1", ScannerEventType.MODIFICATION, 42L);

        // Lifecycle event with null eventType — should preserve fileCount
        useCase.recordEvent("agent-1", null, 42L);

        assertThat(useCase.getMetrics("agent-1").fileCount()).isEqualTo(42L);
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
                        useCase.recordEvent(agentId, ScannerEventType.CREATION, 0L);
                    } else if (j % 3 == 1) {
                        useCase.recordEvent(agentId, ScannerEventType.UNCHANGED, 0L);
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

    // -- ScannerFileEvent / publishFileEvent tests --

    @Test
    void givenEmittedFileEvent_WhenPublished_ThenCallbackReceivesEmittedStatus() {
        CopyOnWriteArrayList<ScannerMetricsEvent> events = new CopyOnWriteArrayList<>();
        useCase.registerRefreshCallback(events::add);

        useCase.publishFileEvent(ScannerFileEvent.emitted("agent-1", "/tmp/test"));

        assertThat(events).hasSize(1);
        assertThat(events.get(0).agentId()).isEqualTo("agent-1");
        assertThat(events.get(0).status()).isEqualTo(ScannerStatus.EMITTING_UPDATES);
    }

    @Test
    void givenFilteredFileEvent_WhenPublished_ThenCallbackReceivesFilteredStatus() {
        CopyOnWriteArrayList<ScannerMetricsEvent> events = new CopyOnWriteArrayList<>();
        useCase.registerRefreshCallback(events::add);

        useCase.publishFileEvent(ScannerFileEvent.filtered("agent-1", "/tmp/test"));

        assertThat(events).hasSize(1);
        assertThat(events.get(0).agentId()).isEqualTo("agent-1");
        assertThat(events.get(0).status()).isEqualTo(ScannerStatus.FILTERED);
    }

    @Test
    void givenErrorFileEvent_WhenPublished_ThenCallbackReceivesErrorStatus() {
        CopyOnWriteArrayList<ScannerMetricsEvent> events = new CopyOnWriteArrayList<>();
        useCase.registerRefreshCallback(events::add);

        useCase.publishFileEvent(ScannerFileEvent.error("agent-1", "disk full"));

        assertThat(events).hasSize(1);
        assertThat(events.get(0).agentId()).isEqualTo("agent-1");
        assertThat(events.get(0).status()).isEqualTo(ScannerStatus.ERROR);
        assertThat(events.get(0).errorMessage()).isEqualTo("disk full");
    }
}

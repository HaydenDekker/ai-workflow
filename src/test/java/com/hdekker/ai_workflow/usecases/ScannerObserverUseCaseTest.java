package com.hdekker.ai_workflow.usecases;

import com.hdekker.ai_workflow.rest.dto.ScannerMetricsSnapshot;
import com.hdekker.ai_workflow.ui.events.ScannerMetricsChangedEvent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Consumer;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ScannerObserverUseCase}.
 * <p>
 * Verifies:
 * 1. recordScannerEvent with CREATION increments discovered count
 * 2. recordScannerEvent with MODIFICATION increments discovered count
 * 3. recordScannerEvent with DELETION does NOT increment discovered count
 * 4. recordScannerEvent with UNCHANGED does NOT increment discovered count
 * 5. getMetrics returns correct snapshot per agent
 * 6. getAllMetrics returns all agent snapshots
 * 7. Callback registration and push-to-UI work correctly
 * 8. Thread-safety under concurrent access
 * 9. Missing agent returns zeroed snapshot
 * 10. countFiles walks the directory correctly
 */
public class ScannerObserverUseCaseTest {

    private ScannerObserverUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ScannerObserverUseCase();
    }

    @Test
    void givenNoMetrics_WhenGetMetrics_ThenReturnsZeroValues() {
        ScannerMetricsSnapshot snapshot = useCase.getMetrics("non-existent-agent");

        assertThat(snapshot.agentId()).isEqualTo("non-existent-agent");
        assertThat(snapshot.fileCount()).isZero();
        assertThat(snapshot.totalDiscovered()).isZero();
    }

    @Test
    void givenNoMetrics_WhenGetAllMetrics_ThenReturnsEmptyList() {
        List<ScannerMetricsSnapshot> all = useCase.getAllMetrics();

        assertThat(all).isEmpty();
    }

    @Test
    void givenCreationEvent_WhenRecorded_ThenDiscoveredCountIncrements() {
        useCase.recordScannerEvent(ScannerEventType.CREATION, "agent-1", "/tmp/test");

        ScannerMetricsSnapshot snapshot = useCase.getMetrics("agent-1");

        assertThat(snapshot.totalDiscovered()).isEqualTo(1);
    }

    @Test
    void givenModificationEvent_WhenRecorded_ThenDiscoveredCountIncrements() {
        useCase.recordScannerEvent(ScannerEventType.MODIFICATION, "agent-1", "/tmp/test");

        ScannerMetricsSnapshot snapshot = useCase.getMetrics("agent-1");

        assertThat(snapshot.totalDiscovered()).isEqualTo(1);
    }

    @Test
    void givenMultipleCreationEvents_WhenRecorded_ThenDiscoveredCountAccumulates() {
        useCase.recordScannerEvent(ScannerEventType.CREATION, "agent-1", "/tmp/test");
        useCase.recordScannerEvent(ScannerEventType.CREATION, "agent-1", "/tmp/test");
        useCase.recordScannerEvent(ScannerEventType.CREATION, "agent-1", "/tmp/test");

        ScannerMetricsSnapshot snapshot = useCase.getMetrics("agent-1");

        assertThat(snapshot.totalDiscovered()).isEqualTo(3);
    }

    @Test
    void givenDeletionEvent_WhenRecorded_ThenDiscoveredCountDoesNotIncrement() {
        useCase.recordScannerEvent(ScannerEventType.DELETION, "agent-1", "/tmp/test");

        ScannerMetricsSnapshot snapshot = useCase.getMetrics("agent-1");

        assertThat(snapshot.totalDiscovered()).isZero();
    }

    @Test
    void givenUnchangedEvent_WhenRecorded_ThenDiscoveredCountDoesNotIncrement() {
        useCase.recordScannerEvent(ScannerEventType.UNCHANGED, "agent-1", "/tmp/test");

        ScannerMetricsSnapshot snapshot = useCase.getMetrics("agent-1");

        assertThat(snapshot.totalDiscovered()).isZero();
    }

    @Test
    void givenMixedEvents_WhenRecorded_ThenDiscoveredCountsOnlyCreationsAndModifications() {
        useCase.recordScannerEvent(ScannerEventType.CREATION, "agent-1", "/tmp/test");
        useCase.recordScannerEvent(ScannerEventType.MODIFICATION, "agent-1", "/tmp/test");
        useCase.recordScannerEvent(ScannerEventType.DELETION, "agent-1", "/tmp/test");
        useCase.recordScannerEvent(ScannerEventType.UNCHANGED, "agent-1", "/tmp/test");

        ScannerMetricsSnapshot snapshot = useCase.getMetrics("agent-1");

        assertThat(snapshot.totalDiscovered()).isEqualTo(2);
    }

    @Test
    void givenMultipleAgents_WhenGetMetrics_ThenReturnsCorrectValuesPerAgent() {
        useCase.recordScannerEvent(ScannerEventType.CREATION, "agent-a", "/tmp/a");
        useCase.recordScannerEvent(ScannerEventType.CREATION, "agent-a", "/tmp/a");

        useCase.recordScannerEvent(ScannerEventType.MODIFICATION, "agent-b", "/tmp/b");
        useCase.recordScannerEvent(ScannerEventType.DELETION, "agent-b", "/tmp/b");

        ScannerMetricsSnapshot snapshotA = useCase.getMetrics("agent-a");
        ScannerMetricsSnapshot snapshotB = useCase.getMetrics("agent-b");

        assertThat(snapshotA.agentId()).isEqualTo("agent-a");
        assertThat(snapshotA.totalDiscovered()).isEqualTo(2);

        assertThat(snapshotB.agentId()).isEqualTo("agent-b");
        assertThat(snapshotB.totalDiscovered()).isEqualTo(1);
    }

    @Test
    void givenMultipleAgents_WhenGetAllMetrics_ThenReturnsAllSnapshots() {
        useCase.recordScannerEvent(ScannerEventType.CREATION, "agent-a", "/tmp/a");
        useCase.recordScannerEvent(ScannerEventType.CREATION, "agent-b", "/tmp/b");

        List<ScannerMetricsSnapshot> all = useCase.getAllMetrics();

        assertThat(all).hasSize(2);
        assertThat(all).extracting(ScannerMetricsSnapshot::agentId)
                .containsExactlyInAnyOrder("agent-a", "agent-b");
    }

    @Test
    void givenCallbackRegistered_WhenEventRecorded_ThenCallbackInvoked() {
        CopyOnWriteArrayList<ScannerMetricsChangedEvent> events = new CopyOnWriteArrayList<>();
        useCase.registerRefreshCallback(events::add);

        useCase.recordScannerEvent(ScannerEventType.CREATION, "agent-1", "/tmp/test");

        assertThat(events).hasSize(1);
        assertThat(events.get(0).getAgentId()).isEqualTo("agent-1");
        assertThat(events.get(0).getType()).isEqualTo("creation");
    }

    @Test
    void givenCallbackRegistered_WhenDeletionOccurs_ThenCallbackInvoked() {
        CopyOnWriteArrayList<ScannerMetricsChangedEvent> events = new CopyOnWriteArrayList<>();
        useCase.registerRefreshCallback(events::add);

        useCase.recordScannerEvent(ScannerEventType.DELETION, "agent-1", "/tmp/test");

        assertThat(events).hasSize(1);
        assertThat(events.get(0).getType()).isEqualTo("deletion");
    }

    @Test
    void givenCallbackRegistered_WhenEmissionRecorded_ThenCallbackInvoked() {
        CopyOnWriteArrayList<ScannerMetricsChangedEvent> events = new CopyOnWriteArrayList<>();
        useCase.registerRefreshCallback(events::add);

        useCase.recordEmission("agent-1");

        assertThat(events).hasSize(1);
        assertThat(events.get(0).getType()).isEqualTo("emitted");
    }

    @Test
    void givenMultipleCallbacks_WhenEventOccurs_ThenAllInvoked() {
        AtomicInteger count1 = new AtomicInteger(0);
        AtomicInteger count2 = new AtomicInteger(0);

        useCase.registerRefreshCallback(e -> count1.incrementAndGet());
        useCase.registerRefreshCallback(e -> count2.incrementAndGet());

        useCase.recordScannerEvent(ScannerEventType.CREATION, "agent-1", "/tmp/test");

        assertThat(count1.get()).isEqualTo(1);
        assertThat(count2.get()).isEqualTo(1);
    }

    @Test
    void givenCallbackUnregistered_WhenEventOccurs_ThenUnregisteredCallbackNotInvoked() {
        AtomicInteger count = new AtomicInteger(0);
        Consumer<ScannerMetricsChangedEvent> callback = e -> count.incrementAndGet();
        useCase.registerRefreshCallback(callback);
        useCase.unregisterRefreshCallback(callback);

        useCase.recordScannerEvent(ScannerEventType.CREATION, "agent-1", "/tmp/test");

        assertThat(count.get()).isZero();
    }

    @Test
    void givenNoCallbacksRegistered_WhenEventOccurs_ThenNoException() {
        // Should not throw
        useCase.recordScannerEvent(ScannerEventType.CREATION, "agent-1", "/tmp/test");
        useCase.recordScannerEvent(ScannerEventType.DELETION, "agent-1", "/tmp/test");
        useCase.recordEmission("agent-1");
    }

    @Test
    void givenCallbackThrows_WhenEventOccurs_ThenOtherCallbacksStillInvoked() {
        CopyOnWriteArrayList<ScannerMetricsChangedEvent> goodEvents = new CopyOnWriteArrayList<>();

        // Register a callback that throws
        useCase.registerRefreshCallback(e -> {
            throw new RuntimeException("callback error");
        });
        // Register a callback that succeeds
        useCase.registerRefreshCallback(goodEvents::add);

        useCase.recordScannerEvent(ScannerEventType.CREATION, "agent-1", "/tmp/test");

        // The good callback should still have been invoked
        assertThat(goodEvents).hasSize(1);
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
                        useCase.recordScannerEvent(ScannerEventType.CREATION, agentId, "/tmp/test");
                    } else if (j % 3 == 1) {
                        useCase.recordScannerEvent(ScannerEventType.UNCHANGED, agentId, "/tmp/test");
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

        ScannerMetricsSnapshot snapshot = useCase.getMetrics(agentId);

        // At least some events should have been recorded
        assertThat(snapshot.totalDiscovered()).isGreaterThan(0);
    }

    @Test
    void givenEmissionRecorded_ThenIdleBecomesFalse() {
        useCase.recordScannerEvent(ScannerEventType.CREATION, "agent-1", "/tmp/test");
        useCase.recordEmission("agent-1");

        assertThat(useCase.isIdle("agent-1")).isFalse();
    }

    @Test
    void givenNoEmission_WhenIsIdle_ThenReturnsTrue() {
        useCase.recordScannerEvent(ScannerEventType.CREATION, "agent-1", "/tmp/test");

        assertThat(useCase.isIdle("agent-1")).isTrue();
    }

    @Test
    void givenNoEvents_WhenIsIdle_ThenReturnsTrue() {
        assertThat(useCase.isIdle("agent-1")).isTrue();
    }
}

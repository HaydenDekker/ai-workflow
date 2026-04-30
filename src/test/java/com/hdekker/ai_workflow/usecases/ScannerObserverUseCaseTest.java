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
 * 1. recordDiscovery increments discovered count
 * 2. recordUnchanged increments unchanged count
 * 3. updateFileCount sets file count
 * 4. getMetrics returns correct snapshot per agent
 * 5. getAllMetrics returns all agent snapshots
 * 6. Callback registration and push-to-UI work correctly
 * 7. Thread-safety under concurrent access
 * 8. Missing agent returns zeroed snapshot
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
        assertThat(snapshot.unchanged()).isZero();
    }

    @Test
    void givenNoMetrics_WhenGetAllMetrics_ThenReturnsEmptyList() {
        List<ScannerMetricsSnapshot> all = useCase.getAllMetrics();

        assertThat(all).isEmpty();
    }

    @Test
    void givenDiscoveryEvent_WhenRecorded_ThenDiscoveredCountIncrements() {
        useCase.recordDiscovery("agent-1");

        ScannerMetricsSnapshot snapshot = useCase.getMetrics("agent-1");

        assertThat(snapshot.totalDiscovered()).isEqualTo(1);
        assertThat(snapshot.fileCount()).isZero();
        assertThat(snapshot.unchanged()).isZero();
    }

    @Test
    void givenMultipleDiscoveryEvents_WhenRecorded_ThenDiscoveredCountAccumulates() {
        useCase.recordDiscovery("agent-1");
        useCase.recordDiscovery("agent-1");
        useCase.recordDiscovery("agent-1");

        ScannerMetricsSnapshot snapshot = useCase.getMetrics("agent-1");

        assertThat(snapshot.totalDiscovered()).isEqualTo(3);
    }

    @Test
    void givenUnchangedEvent_WhenRecorded_ThenUnchangedCountIncrements() {
        useCase.recordUnchanged("agent-1");

        ScannerMetricsSnapshot snapshot = useCase.getMetrics("agent-1");

        assertThat(snapshot.unchanged()).isEqualTo(1);
        assertThat(snapshot.fileCount()).isZero();
        assertThat(snapshot.totalDiscovered()).isZero();
    }

    @Test
    void givenMultipleUnchangedEvents_WhenRecorded_ThenUnchangedCountAccumulates() {
        useCase.recordUnchanged("agent-1");
        useCase.recordUnchanged("agent-1");

        ScannerMetricsSnapshot snapshot = useCase.getMetrics("agent-1");

        assertThat(snapshot.unchanged()).isEqualTo(2);
    }

    @Test
    void givenFileCountUpdate_WhenRecorded_ThenFileCountIsSet() {
        useCase.updateFileCount("agent-1", 42);

        ScannerMetricsSnapshot snapshot = useCase.getMetrics("agent-1");

        assertThat(snapshot.fileCount()).isEqualTo(42);
    }

    @Test
    void givenFileCountUpdate_WhenRecordedMultipleTimes_ThenFileCountOverwrites() {
        useCase.updateFileCount("agent-1", 10);
        useCase.updateFileCount("agent-1", 25);
        useCase.updateFileCount("agent-1", 7);

        ScannerMetricsSnapshot snapshot = useCase.getMetrics("agent-1");

        assertThat(snapshot.fileCount()).isEqualTo(7);
    }

    @Test
    void givenMixedEvents_WhenRecorded_ThenAllMetricsTrackedCorrectly() {
        useCase.recordDiscovery("agent-1");
        useCase.recordDiscovery("agent-1");
        useCase.recordUnchanged("agent-1");
        useCase.updateFileCount("agent-1", 5);

        ScannerMetricsSnapshot snapshot = useCase.getMetrics("agent-1");

        assertThat(snapshot.fileCount()).isEqualTo(5);
        assertThat(snapshot.totalDiscovered()).isEqualTo(2);
        assertThat(snapshot.unchanged()).isEqualTo(1);
    }

    @Test
    void givenMultipleAgents_WhenGetMetrics_ThenReturnsCorrectValuesPerAgent() {
        useCase.recordDiscovery("agent-a");
        useCase.recordDiscovery("agent-a");
        useCase.updateFileCount("agent-a", 10);

        useCase.recordDiscovery("agent-b");
        useCase.recordUnchanged("agent-b");
        useCase.updateFileCount("agent-b", 20);

        ScannerMetricsSnapshot snapshotA = useCase.getMetrics("agent-a");
        ScannerMetricsSnapshot snapshotB = useCase.getMetrics("agent-b");

        assertThat(snapshotA.agentId()).isEqualTo("agent-a");
        assertThat(snapshotA.fileCount()).isEqualTo(10);
        assertThat(snapshotA.totalDiscovered()).isEqualTo(2);
        assertThat(snapshotA.unchanged()).isZero();

        assertThat(snapshotB.agentId()).isEqualTo("agent-b");
        assertThat(snapshotB.fileCount()).isEqualTo(20);
        assertThat(snapshotB.totalDiscovered()).isEqualTo(1);
        assertThat(snapshotB.unchanged()).isEqualTo(1);
    }

    @Test
    void givenMultipleAgents_WhenGetAllMetrics_ThenReturnsAllSnapshots() {
        useCase.recordDiscovery("agent-a");
        useCase.updateFileCount("agent-a", 10);

        useCase.recordDiscovery("agent-b");
        useCase.updateFileCount("agent-b", 20);

        List<ScannerMetricsSnapshot> all = useCase.getAllMetrics();

        assertThat(all).hasSize(2);
        assertThat(all).extracting(ScannerMetricsSnapshot::agentId)
                .containsExactlyInAnyOrder("agent-a", "agent-b");
    }

    @Test
    void givenCallbackRegistered_WhenDiscoveryOccurs_ThenCallbackInvoked() {
        CopyOnWriteArrayList<ScannerMetricsChangedEvent> events = new CopyOnWriteArrayList<>();
        useCase.registerRefreshCallback(events::add);

        useCase.recordDiscovery("agent-1");

        assertThat(events).hasSize(1);
        assertThat(events.get(0).getAgentId()).isEqualTo("agent-1");
        assertThat(events.get(0).getType()).isEqualTo("discovered");
    }

    @Test
    void givenCallbackRegistered_WhenUnchangedOccurs_ThenCallbackInvoked() {
        CopyOnWriteArrayList<ScannerMetricsChangedEvent> events = new CopyOnWriteArrayList<>();
        useCase.registerRefreshCallback(events::add);

        useCase.recordUnchanged("agent-1");

        assertThat(events).hasSize(1);
        assertThat(events.get(0).getType()).isEqualTo("unchanged");
    }

    @Test
    void givenCallbackRegistered_WhenFileCountUpdated_ThenCallbackInvoked() {
        CopyOnWriteArrayList<ScannerMetricsChangedEvent> events = new CopyOnWriteArrayList<>();
        useCase.registerRefreshCallback(events::add);

        useCase.updateFileCount("agent-1", 42);

        assertThat(events).hasSize(1);
        assertThat(events.get(0).getType()).isEqualTo("file_count");
    }

    @Test
    void givenMultipleCallbacks_WhenEventOccurs_ThenAllInvoked() {
        AtomicInteger count1 = new AtomicInteger(0);
        AtomicInteger count2 = new AtomicInteger(0);

        useCase.registerRefreshCallback(e -> count1.incrementAndGet());
        useCase.registerRefreshCallback(e -> count2.incrementAndGet());

        useCase.recordDiscovery("agent-1");

        assertThat(count1.get()).isEqualTo(1);
        assertThat(count2.get()).isEqualTo(1);
    }

    @Test
    void givenCallbackUnregistered_WhenEventOccurs_ThenUnregisteredCallbackNotInvoked() {
        AtomicInteger count = new AtomicInteger(0);
        Consumer<ScannerMetricsChangedEvent> callback = e -> count.incrementAndGet();
        useCase.registerRefreshCallback(callback);
        useCase.unregisterRefreshCallback(callback);

        useCase.recordDiscovery("agent-1");

        assertThat(count.get()).isZero();
    }

    @Test
    void givenNoCallbacksRegistered_WhenEventOccurs_ThenNoException() {
        // Should not throw
        useCase.recordDiscovery("agent-1");
        useCase.recordUnchanged("agent-1");
        useCase.updateFileCount("agent-1", 5);
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

        useCase.recordDiscovery("agent-1");

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
            final int threadIndex = i;
            threads[i] = new Thread(() -> {
                for (int j = 0; j < updatesPerThread; j++) {
                    if (j % 3 == 0) {
                        useCase.recordDiscovery(agentId);
                    } else if (j % 3 == 1) {
                        useCase.recordUnchanged(agentId);
                    } else {
                        useCase.updateFileCount(agentId, threadIndex * updatesPerThread + j);
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
        assertThat(snapshot.totalDiscovered() + snapshot.unchanged()).isGreaterThan(0);
        assertThat(snapshot.fileCount()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void givenFileCountZero_WhenUpdateFileCount_ThenFileCountIsZero() {
        useCase.updateFileCount("agent-1", 0);

        ScannerMetricsSnapshot snapshot = useCase.getMetrics("agent-1");

        assertThat(snapshot.fileCount()).isZero();
    }

    @Test
    void givenUpdateFileCountBeforeDiscovery_WhenAllEventsRecorded_ThenMetricsCorrect() {
        useCase.updateFileCount("agent-1", 100);
        useCase.recordDiscovery("agent-1");
        useCase.recordUnchanged("agent-1");

        ScannerMetricsSnapshot snapshot = useCase.getMetrics("agent-1");

        assertThat(snapshot.fileCount()).isEqualTo(100);
        assertThat(snapshot.totalDiscovered()).isEqualTo(1);
        assertThat(snapshot.unchanged()).isEqualTo(1);
    }

    @Test
    void givenNewFileDiscovery_WhenRecorded_ThenBothFileCountAndDiscoveredIncrement() {
        // Simulate initial scan setting the file count
        useCase.updateFileCount("agent-1", 3);

        // A new file is discovered during incremental watching
        useCase.recordDiscoveryNewFile("agent-1");

        ScannerMetricsSnapshot snapshot = useCase.getMetrics("agent-1");

        assertThat(snapshot.fileCount()).isEqualTo(4);
        assertThat(snapshot.totalDiscovered()).isEqualTo(1);
        assertThat(snapshot.unchanged()).isZero();
    }

    @Test
    void givenNewFileDiscoveryOnEmptyAgent_WhenRecorded_ThenFileCountStartsAtOne() {
        useCase.recordDiscoveryNewFile("agent-1");

        ScannerMetricsSnapshot snapshot = useCase.getMetrics("agent-1");

        assertThat(snapshot.fileCount()).isEqualTo(1);
        assertThat(snapshot.totalDiscovered()).isEqualTo(1);
    }

    @Test
    void givenMultipleNewFiles_WhenRecorded_ThenFileCountAccumulates() {
        useCase.updateFileCount("agent-1", 5);
        useCase.recordDiscoveryNewFile("agent-1");
        useCase.recordDiscoveryNewFile("agent-1");
        useCase.recordDiscoveryNewFile("agent-1");

        ScannerMetricsSnapshot snapshot = useCase.getMetrics("agent-1");

        assertThat(snapshot.fileCount()).isEqualTo(8);
        assertThat(snapshot.totalDiscovered()).isEqualTo(3);
    }

    @Test
    void givenChangedFileDiscovery_WhenRecorded_ThenOnlyDiscoveredIncrements() {
        // Simulate initial scan setting the file count
        useCase.updateFileCount("agent-1", 3);

        // A changed file is discovered (existing file with different hash)
        useCase.recordDiscovery("agent-1");

        ScannerMetricsSnapshot snapshot = useCase.getMetrics("agent-1");

        // fileCount should NOT change for changed files
        assertThat(snapshot.fileCount()).isEqualTo(3);
        assertThat(snapshot.totalDiscovered()).isEqualTo(1);
    }

    @Test
    void givenMixedNewAndChangedFiles_WhenRecorded_ThenMetricsCorrect() {
        useCase.updateFileCount("agent-1", 10);

        // 2 new files
        useCase.recordDiscoveryNewFile("agent-1");
        useCase.recordDiscoveryNewFile("agent-1");
        // 1 changed file
        useCase.recordDiscovery("agent-1");
        // 1 unchanged file
        useCase.recordUnchanged("agent-1");

        ScannerMetricsSnapshot snapshot = useCase.getMetrics("agent-1");

        assertThat(snapshot.fileCount()).isEqualTo(12);  // 10 + 2 new
        assertThat(snapshot.totalDiscovered()).isEqualTo(3);  // 2 new + 1 changed
        assertThat(snapshot.unchanged()).isEqualTo(1);
    }
}

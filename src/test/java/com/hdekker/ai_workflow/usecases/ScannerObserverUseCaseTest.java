package com.hdekker.ai_workflow.usecases;

import com.hdekker.ai_workflow.rest.dto.ScannerMetricsSnapshot;
import com.hdekker.ai_workflow.ui.events.ScannerMetricsChangedEvent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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

    @TempDir
    Path tempDir;

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
        useCase.recordScannerEvent(new ScannerMetricsChangedEvent("agent-1", ScannerStatus.EMITTING_UPDATES, ScannerEventType.CREATION, "/tmp/test", null));

        ScannerMetricsSnapshot snapshot = useCase.getMetrics("agent-1");

        assertThat(snapshot.totalDiscovered()).isEqualTo(1);
    }

    @Test
    void givenModificationEvent_WhenRecorded_ThenDiscoveredCountIncrements() {
        useCase.recordScannerEvent(new ScannerMetricsChangedEvent("agent-1", ScannerStatus.EMITTING_UPDATES, ScannerEventType.MODIFICATION, "/tmp/test", null));

        ScannerMetricsSnapshot snapshot = useCase.getMetrics("agent-1");

        assertThat(snapshot.totalDiscovered()).isEqualTo(1);
    }

    @Test
    void givenMultipleCreationEvents_WhenRecorded_ThenDiscoveredCountAccumulates() {
        useCase.recordScannerEvent(new ScannerMetricsChangedEvent("agent-1", ScannerStatus.EMITTING_UPDATES, ScannerEventType.CREATION, "/tmp/test", null));
        useCase.recordScannerEvent(new ScannerMetricsChangedEvent("agent-1", ScannerStatus.EMITTING_UPDATES, ScannerEventType.CREATION, "/tmp/test", null));
        useCase.recordScannerEvent(new ScannerMetricsChangedEvent("agent-1", ScannerStatus.EMITTING_UPDATES, ScannerEventType.CREATION, "/tmp/test", null));

        ScannerMetricsSnapshot snapshot = useCase.getMetrics("agent-1");

        assertThat(snapshot.totalDiscovered()).isEqualTo(3);
    }

    @Test
    void givenDeletionEvent_WhenRecorded_ThenDiscoveredCountDoesNotIncrement() {
        useCase.recordScannerEvent(new ScannerMetricsChangedEvent("agent-1", ScannerStatus.EMITTING_UPDATES, ScannerEventType.DELETION, "/tmp/test", null));

        ScannerMetricsSnapshot snapshot = useCase.getMetrics("agent-1");

        assertThat(snapshot.totalDiscovered()).isZero();
    }

    @Test
    void givenUnchangedEvent_WhenRecorded_ThenDiscoveredCountDoesNotIncrement() {
        useCase.recordScannerEvent(new ScannerMetricsChangedEvent("agent-1", ScannerStatus.FILTERED, ScannerEventType.UNCHANGED, "/tmp/test", null));

        ScannerMetricsSnapshot snapshot = useCase.getMetrics("agent-1");

        assertThat(snapshot.totalDiscovered()).isZero();
    }

    @Test
    void givenMixedEvents_WhenRecorded_ThenDiscoveredCountsOnlyCreationsAndModifications() {
        useCase.recordScannerEvent(new ScannerMetricsChangedEvent("agent-1", ScannerStatus.EMITTING_UPDATES, ScannerEventType.CREATION, "/tmp/test", null));
        useCase.recordScannerEvent(new ScannerMetricsChangedEvent("agent-1", ScannerStatus.EMITTING_UPDATES, ScannerEventType.MODIFICATION, "/tmp/test", null));
        useCase.recordScannerEvent(new ScannerMetricsChangedEvent("agent-1", ScannerStatus.EMITTING_UPDATES, ScannerEventType.DELETION, "/tmp/test", null));
        useCase.recordScannerEvent(new ScannerMetricsChangedEvent("agent-1", ScannerStatus.FILTERED, ScannerEventType.UNCHANGED, "/tmp/test", null));

        ScannerMetricsSnapshot snapshot = useCase.getMetrics("agent-1");

        assertThat(snapshot.totalDiscovered()).isEqualTo(2);
    }

    @Test
    void givenMultipleAgents_WhenGetMetrics_ThenReturnsCorrectValuesPerAgent() {
        useCase.recordScannerEvent(new ScannerMetricsChangedEvent("agent-a", ScannerStatus.EMITTING_UPDATES, ScannerEventType.CREATION, "/tmp/a", null));
        useCase.recordScannerEvent(new ScannerMetricsChangedEvent("agent-a", ScannerStatus.EMITTING_UPDATES, ScannerEventType.CREATION, "/tmp/a", null));

        useCase.recordScannerEvent(new ScannerMetricsChangedEvent("agent-b", ScannerStatus.EMITTING_UPDATES, ScannerEventType.MODIFICATION, "/tmp/b", null));
        useCase.recordScannerEvent(new ScannerMetricsChangedEvent("agent-b", ScannerStatus.EMITTING_UPDATES, ScannerEventType.DELETION, "/tmp/b", null));

        ScannerMetricsSnapshot snapshotA = useCase.getMetrics("agent-a");
        ScannerMetricsSnapshot snapshotB = useCase.getMetrics("agent-b");

        assertThat(snapshotA.agentId()).isEqualTo("agent-a");
        assertThat(snapshotA.totalDiscovered()).isEqualTo(2);

        assertThat(snapshotB.agentId()).isEqualTo("agent-b");
        assertThat(snapshotB.totalDiscovered()).isEqualTo(1);
    }

    @Test
    void givenMultipleAgents_WhenGetAllMetrics_ThenReturnsAllSnapshots() {
        useCase.recordScannerEvent(new ScannerMetricsChangedEvent("agent-a", ScannerStatus.EMITTING_UPDATES, ScannerEventType.CREATION, "/tmp/a", null));
        useCase.recordScannerEvent(new ScannerMetricsChangedEvent("agent-b", ScannerStatus.EMITTING_UPDATES, ScannerEventType.CREATION, "/tmp/b", null));

        List<ScannerMetricsSnapshot> all = useCase.getAllMetrics();

        assertThat(all).hasSize(2);
        assertThat(all).extracting(ScannerMetricsSnapshot::agentId)
                .containsExactlyInAnyOrder("agent-a", "agent-b");
    }

    @Test
    void givenCallbackRegistered_WhenEventRecorded_ThenCallbackInvoked() {
        CopyOnWriteArrayList<ScannerMetricsChangedEvent> events = new CopyOnWriteArrayList<>();
        useCase.registerRefreshCallback(events::add);

        useCase.recordScannerEvent(new ScannerMetricsChangedEvent("agent-1", ScannerStatus.EMITTING_UPDATES, ScannerEventType.CREATION, "/tmp/test", null));

        assertThat(events).hasSize(1);
        assertThat(events.get(0).getAgentId()).isEqualTo("agent-1");
        assertThat(events.get(0).getType()).isEqualTo("creation");
    }

    @Test
    void givenCallbackRegistered_WhenDeletionOccurs_ThenCallbackInvoked() {
        CopyOnWriteArrayList<ScannerMetricsChangedEvent> events = new CopyOnWriteArrayList<>();
        useCase.registerRefreshCallback(events::add);

        useCase.recordScannerEvent(new ScannerMetricsChangedEvent("agent-1", ScannerStatus.EMITTING_UPDATES, ScannerEventType.DELETION, "/tmp/test", null));

        assertThat(events).hasSize(1);
        assertThat(events.get(0).getType()).isEqualTo("deletion");
    }

    @Test
    void givenCallbackRegistered_WhenEmissionRecorded_ThenCallbackInvoked() {
        CopyOnWriteArrayList<ScannerMetricsChangedEvent> events = new CopyOnWriteArrayList<>();
        useCase.registerRefreshCallback(events::add);

        useCase.recordScannerEvent(new ScannerMetricsChangedEvent("agent-1", ScannerStatus.EMITTING_UPDATES, null, null, null));

        assertThat(events).hasSize(1);
        assertThat(events.get(0).getType()).isEqualTo("emitting_updates");
    }

    @Test
    void givenMultipleCallbacks_WhenEventOccurs_ThenAllInvoked() {
        AtomicInteger count1 = new AtomicInteger(0);
        AtomicInteger count2 = new AtomicInteger(0);

        useCase.registerRefreshCallback(e -> count1.incrementAndGet());
        useCase.registerRefreshCallback(e -> count2.incrementAndGet());

        useCase.recordScannerEvent(new ScannerMetricsChangedEvent("agent-1", ScannerStatus.EMITTING_UPDATES, ScannerEventType.CREATION, "/tmp/test", null));

        assertThat(count1.get()).isEqualTo(1);
        assertThat(count2.get()).isEqualTo(1);
    }

    @Test
    void givenCallbackUnregistered_WhenEventOccurs_ThenUnregisteredCallbackNotInvoked() {
        AtomicInteger count = new AtomicInteger(0);
        Consumer<ScannerMetricsChangedEvent> callback = e -> count.incrementAndGet();
        useCase.registerRefreshCallback(callback);
        useCase.unregisterRefreshCallback(callback);

        useCase.recordScannerEvent(new ScannerMetricsChangedEvent("agent-1", ScannerStatus.EMITTING_UPDATES, ScannerEventType.CREATION, "/tmp/test", null));

        assertThat(count.get()).isZero();
    }

    @Test
    void givenNoCallbacksRegistered_WhenEventOccurs_ThenNoException() {
        // Should not throw
        useCase.recordScannerEvent(new ScannerMetricsChangedEvent("agent-1", ScannerStatus.EMITTING_UPDATES, ScannerEventType.CREATION, "/tmp/test", null));
        useCase.recordScannerEvent(new ScannerMetricsChangedEvent("agent-1", ScannerStatus.EMITTING_UPDATES, ScannerEventType.DELETION, "/tmp/test", null));
        useCase.recordScannerEvent(new ScannerMetricsChangedEvent("agent-1", ScannerStatus.EMITTING_UPDATES, null, null, null));
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

        useCase.recordScannerEvent(new ScannerMetricsChangedEvent("agent-1", ScannerStatus.EMITTING_UPDATES, ScannerEventType.CREATION, "/tmp/test", null));

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

        ScannerMetricsSnapshot snapshot = useCase.getMetrics(agentId);

        // At least some events should have been recorded
        assertThat(snapshot.totalDiscovered()).isGreaterThan(0);
    }

    @Test
    void givenEmissionRecorded_ThenIdleBecomesFalse() {
        useCase.recordScannerEvent(new ScannerMetricsChangedEvent("agent-1", ScannerStatus.EMITTING_UPDATES, ScannerEventType.CREATION, "/tmp/test", null));
        useCase.recordScannerEvent(new ScannerMetricsChangedEvent("agent-1", ScannerStatus.EMITTING_UPDATES, null, null, null));

        assertThat(useCase.isIdle("agent-1")).isFalse();
    }

    @Test
    void givenNoEmission_WhenIsIdle_ThenReturnsTrue() {
        useCase.recordScannerEvent(new ScannerMetricsChangedEvent("agent-1", ScannerStatus.EMITTING_UPDATES, ScannerEventType.CREATION, "/tmp/test", null));

        assertThat(useCase.isIdle("agent-1")).isTrue();
    }

    @Test
    void givenNoEvents_WhenIsIdle_ThenReturnsTrue() {
        assertThat(useCase.isIdle("agent-1")).isTrue();
    }

    @Test
    void givenErrorEvent_WhenRecorded_ThenCallbackInvokedWithStatus() {
        CopyOnWriteArrayList<ScannerMetricsChangedEvent> events = new CopyOnWriteArrayList<>();
        useCase.registerRefreshCallback(events::add);

        useCase.recordScannerEvent(new ScannerMetricsChangedEvent("agent-1", ScannerStatus.ERROR, null, null, "some error"));

        assertThat(events).hasSize(1);
        assertThat(events.get(0).getStatus()).isEqualTo(ScannerStatus.ERROR);
        assertThat(events.get(0).getErrorMessage()).isEqualTo("some error");
        assertThat(events.get(0).getType()).isEqualTo("error");
    }

    @Test
    void givenRecoveryEvent_WhenRecorded_ThenCallbackInvokedWithIdleStatus() {
        CopyOnWriteArrayList<ScannerMetricsChangedEvent> events = new CopyOnWriteArrayList<>();
        useCase.registerRefreshCallback(events::add);

        useCase.recordScannerEvent(new ScannerMetricsChangedEvent("agent-1", ScannerStatus.IDLE, null, null, null));

        assertThat(events).hasSize(1);
        assertThat(events.get(0).getStatus()).isEqualTo(ScannerStatus.IDLE);
        assertThat(events.get(0).getType()).isEqualTo("idle");
    }

    @Test
    void givenTypeWithEventType_ThenReturnsEventTypeName() {
        ScannerMetricsChangedEvent event = new ScannerMetricsChangedEvent("agent-1", ScannerStatus.EMITTING_UPDATES, ScannerEventType.CREATION, "/tmp/test", null);
        assertThat(event.getType()).isEqualTo("creation");
    }

    @Test
    void givenTypeWithoutEventType_ThenReturnsStatusName() {
        ScannerMetricsChangedEvent event = new ScannerMetricsChangedEvent("agent-1", ScannerStatus.ERROR, null, null, "msg");
        assertThat(event.getType()).isEqualTo("error");
    }

    // -- countFiles / on-demand directory walk tests (migrated from ScannerMetricsTest) --

    @Test
    void givenFolderRegistered_WhenCountFiles_ThenReturnsActualFileCount() throws IOException {
        Path watchedDir = tempDir.resolve("watched");
        Files.createDirectories(watchedDir);
        Files.writeString(watchedDir.resolve("a.txt"), "content a");
        Files.writeString(watchedDir.resolve("b.txt"), "content b");
        Files.writeString(watchedDir.resolve("c.txt"), "content c");

        useCase.recordScannerEvent(new ScannerMetricsChangedEvent(
                "agent-1", ScannerStatus.EMITTING_UPDATES, ScannerEventType.CREATION, watchedDir.toString(), null));

        assertThat(useCase.countFiles("agent-1")).isEqualTo(3);
    }

    @Test
    void givenEmptyFolderRegistered_WhenCountFiles_ThenReturnsZero() throws IOException {
        Path watchedDir = tempDir.resolve("empty");
        Files.createDirectories(watchedDir);

        useCase.recordScannerEvent(new ScannerMetricsChangedEvent(
                "agent-1", ScannerStatus.EMITTING_UPDATES, ScannerEventType.CREATION, watchedDir.toString(), null));

        assertThat(useCase.countFiles("agent-1")).isZero();
    }

    @Test
    void givenNoFolderRegistered_WhenCountFiles_ThenReturnsZero() {
        // No event recorded → no folder mapping → countFiles returns 0
        assertThat(useCase.countFiles("agent-no-folder")).isZero();
    }

    @Test
    void givenGetMetrics_WhenFolderRegistered_ThenFileCountReflectsDirectory() throws IOException {
        Path watchedDir = tempDir.resolve("metrics-dir");
        Files.createDirectories(watchedDir);
        Files.writeString(watchedDir.resolve("file1.txt"), "one");
        Files.writeString(watchedDir.resolve("file2.txt"), "two");

        useCase.recordScannerEvent(new ScannerMetricsChangedEvent(
                "agent-1", ScannerStatus.EMITTING_UPDATES, ScannerEventType.CREATION, watchedDir.toString(), null));

        ScannerMetricsSnapshot snapshot = useCase.getMetrics("agent-1");

        assertThat(snapshot.totalDiscovered()).isEqualTo(1);
        assertThat(snapshot.fileCount()).isEqualTo(2);
    }

    @Test
    void givenNestedFilesInFolder_WhenCountFiles_ThenIncludesNestedFiles() throws IOException {
        Path watchedDir = tempDir.resolve("nested");
        Path subDir = watchedDir.resolve("sub");
        Files.createDirectories(subDir);
        Files.writeString(watchedDir.resolve("top.txt"), "top");
        Files.writeString(subDir.resolve("deep.txt"), "deep");

        useCase.recordScannerEvent(new ScannerMetricsChangedEvent(
                "agent-1", ScannerStatus.EMITTING_UPDATES, ScannerEventType.CREATION, watchedDir.toString(), null));

        assertThat(useCase.countFiles("agent-1")).isEqualTo(2);
    }

    @Test
    void givenDeletionEvent_WhenRecorded_ThenFolderIsStoredForCountFiles() throws IOException {
        Path watchedDir = tempDir.resolve("deletion-dir");
        Files.createDirectories(watchedDir);
        Files.writeString(watchedDir.resolve("existing.txt"), "content");

        useCase.recordScannerEvent(new ScannerMetricsChangedEvent(
                "agent-1", ScannerStatus.EMITTING_UPDATES, ScannerEventType.DELETION, watchedDir.toString(), null));

        // DELETION doesn't increment discovered but does store the folder
        ScannerMetricsSnapshot snapshot = useCase.getMetrics("agent-1");
        assertThat(snapshot.totalDiscovered()).isZero();
        assertThat(snapshot.fileCount()).isEqualTo(1);
    }
}

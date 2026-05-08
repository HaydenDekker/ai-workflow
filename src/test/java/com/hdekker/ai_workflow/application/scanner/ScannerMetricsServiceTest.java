package com.hdekker.ai_workflow.application.scanner;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.hdekker.ai_workflow.domain.scanner.ScannerEventType;
import com.hdekker.ai_workflow.domain.scanner.ScannerMetrics;

/**
 * Unit tests for {@link ScannerMetricsService}.
 * <p>
 * Tests the pure metrics storage concern — no callbacks, no push.
 * Callback/push tests live in {@link ScannerEventBusTest}.
 */
class ScannerMetricsServiceTest {

    private ScannerMetricsService metricsService;

    @BeforeEach
    void setUp() {
        metricsService = new ScannerMetricsService();
    }

    // -- fileCount domain field tests --

    @Test
    void givenMetricsCreated_ThenFileCountIsZero() {
        ScannerMetrics metrics = metricsService.getMetrics("agent-1");
        assertThat(metrics.fileCount()).isZero();
    }

    @Test
    void givenEventWithFileCount_WhenRecorded_ThenStoredInMetrics() {
        metricsService.recordEvent("agent-1", ScannerEventType.CREATION, 42L);

        ScannerMetrics metrics = metricsService.getMetrics("agent-1");
        assertThat(metrics.fileCount()).isEqualTo(42L);
    }

    @Test
    void givenMetricsWithFileCount_ThenFileCountReturned() {
        metricsService.recordEvent("agent-1", ScannerEventType.CREATION, 100L);
        metricsService.recordEvent("agent-1", ScannerEventType.CREATION, 150L);

        ScannerMetrics metrics = metricsService.getMetrics("agent-1");
        assertThat(metrics.fileCount()).isEqualTo(150L);
        assertThat(metrics.totalDiscovered()).isEqualTo(2);
    }

    @Test
    void givenEventWithoutFileCount_WhenRecorded_ThenFileCountDefaultsToZero() {
        metricsService.recordEvent("agent-1", ScannerEventType.CREATION);

        ScannerMetrics metrics = metricsService.getMetrics("agent-1");
        assertThat(metrics.fileCount()).isZero();
    }

    @Test
    void givenDeletionEvent_WhenRecordedWithFileCount_ThenFileCountStored() {
        metricsService.recordEvent("agent-1", ScannerEventType.DELETION, 33L);

        ScannerMetrics metrics = metricsService.getMetrics("agent-1");
        assertThat(metrics.fileCount()).isEqualTo(33L);
        assertThat(metrics.totalDiscovered()).isZero();
    }

    // -- Metrics counting tests --

    @Test
    void givenNoEvents_WhenGettingMetrics_ThenReturnsEmptyMetrics() {
        ScannerMetrics metrics = metricsService.getMetrics("agent-1");
        assertThat(metrics.agentId()).isEqualTo("agent-1");
        assertThat(metrics.totalDiscovered()).isZero();
        assertThat(metrics.lastEmissionTimestamp()).isNull();
    }

    @Test
    void givenCreationEvent_WhenGettingMetrics_ThenDiscoveredIsOne() {
        metricsService.recordEvent("agent-1", ScannerEventType.CREATION, 0L);

        ScannerMetrics metrics = metricsService.getMetrics("agent-1");
        assertThat(metrics.totalDiscovered()).isEqualTo(1);
    }

    @Test
    void givenModificationEvent_WhenGettingMetrics_ThenDiscoveredIsOne() {
        metricsService.recordEvent("agent-1", ScannerEventType.MODIFICATION, 0L);

        ScannerMetrics metrics = metricsService.getMetrics("agent-1");
        assertThat(metrics.totalDiscovered()).isEqualTo(1);
    }

    @Test
    void givenDeletionEvent_WhenGettingMetrics_ThenDiscoveredIsZero() {
        metricsService.recordEvent("agent-1", ScannerEventType.DELETION, 0L);

        ScannerMetrics metrics = metricsService.getMetrics("agent-1");
        assertThat(metrics.totalDiscovered()).isZero();
    }

    @Test
    void givenUnchangedEvent_WhenGettingMetrics_ThenDiscoveredIsZero() {
        metricsService.recordEvent("agent-1", ScannerEventType.UNCHANGED, 0L);

        ScannerMetrics metrics = metricsService.getMetrics("agent-1");
        assertThat(metrics.totalDiscovered()).isZero();
    }

    @Test
    void givenMultipleCreations_WhenGettingMetrics_ThenAccumulates() {
        metricsService.recordEvent("agent-1", ScannerEventType.CREATION, 0L);
        metricsService.recordEvent("agent-1", ScannerEventType.CREATION, 0L);
        metricsService.recordEvent("agent-1", ScannerEventType.CREATION, 0L);

        ScannerMetrics metrics = metricsService.getMetrics("agent-1");
        assertThat(metrics.totalDiscovered()).isEqualTo(3);
    }

    @Test
    void givenMixedEvents_WhenGettingMetrics_ThenCountsOnlyCreationAndModification() {
        metricsService.recordEvent("agent-1", ScannerEventType.CREATION, 0L);
        metricsService.recordEvent("agent-1", ScannerEventType.MODIFICATION, 0L);
        metricsService.recordEvent("agent-1", ScannerEventType.DELETION, 0L);
        metricsService.recordEvent("agent-1", ScannerEventType.UNCHANGED, 0L);

        ScannerMetrics metrics = metricsService.getMetrics("agent-1");
        assertThat(metrics.totalDiscovered()).isEqualTo(2);
    }

    @Test
    void givenMultipleAgents_WhenGettingMetrics_ThenReturnsCorrectPerAgent() {
        metricsService.recordEvent("agent-a", ScannerEventType.CREATION, 0L);
        metricsService.recordEvent("agent-a", ScannerEventType.CREATION, 0L);
        metricsService.recordEvent("agent-b", ScannerEventType.MODIFICATION, 0L);
        metricsService.recordEvent("agent-b", ScannerEventType.DELETION, 0L);

        assertThat(metricsService.getMetrics("agent-a").totalDiscovered()).isEqualTo(2);
        assertThat(metricsService.getMetrics("agent-b").totalDiscovered()).isEqualTo(1);
    }

    // -- getAllMetrics tests --

    @Test
    void givenNoMetrics_WhenGetAllMetrics_ThenReturnsEmptyList() {
        assertThat(metricsService.getAllMetrics()).isEmpty();
    }

    @Test
    void givenMultipleAgents_WhenGetAllMetrics_ThenReturnsAllSnapshots() {
        metricsService.recordEvent("agent-a", ScannerEventType.CREATION, 0L);
        metricsService.recordEvent("agent-b", ScannerEventType.CREATION, 0L);

        List<ScannerMetrics> all = metricsService.getAllMetrics();

        assertThat(all).hasSize(2);
        assertThat(all).extracting(ScannerMetrics::agentId)
                .containsExactlyInAnyOrder("agent-a", "agent-b");
    }

    // -- isIdle tests --

    @Test
    void givenEmissionRecorded_ThenIdleBecomesFalse() {
        metricsService.recordEvent("agent-1", ScannerEventType.CREATION, 0L);
        metricsService.recordEmission("agent-1");

        assertThat(metricsService.isIdle("agent-1")).isFalse();
    }

    @Test
    void givenNoEmission_WhenIsIdle_ThenReturnsTrue() {
        metricsService.recordEvent("agent-1", ScannerEventType.CREATION, 0L);

        assertThat(metricsService.isIdle("agent-1")).isTrue();
    }

    @Test
    void givenNoEvents_WhenIsIdle_ThenReturnsTrue() {
        assertThat(metricsService.isIdle("agent-1")).isTrue();
    }

    // -- lifecycle event tests --

    @Test
    void givenLifecycleEvent_WhenRecordedWithFileCount_ThenFileCountStored() {
        metricsService.recordEvent("agent-1", null, 20L);

        ScannerMetrics metrics = metricsService.getMetrics("agent-1");
        assertThat(metrics.fileCount()).isEqualTo(20L);
    }

    // -- fileCount persistence across emission events --

    @Test
    void givenFileEventWithCount_WhenEmissionRecorded_ThenFileCountPreserved() {
        metricsService.recordEvent("agent-1", ScannerEventType.CREATION, 7L);
        assertThat(metricsService.getMetrics("agent-1").fileCount()).isEqualTo(7L);

        metricsService.recordEmission("agent-1");
        metricsService.recordEvent("agent-1", null, 7L);

        assertThat(metricsService.getMetrics("agent-1").fileCount()).isEqualTo(7L);
    }

    @Test
    void givenFileEventWithCount_WhenLifecycleEventRecorded_ThenFileCountPreserved() {
        metricsService.recordEvent("agent-1", ScannerEventType.MODIFICATION, 42L);

        metricsService.recordEvent("agent-1", null, 42L);

        assertThat(metricsService.getMetrics("agent-1").fileCount()).isEqualTo(42L);
    }

    // -- concurrency test --

    @Test
    void givenConcurrentAccess_WhenMultipleThreadsUpdate_ThenMetricsConsistent()
            throws InterruptedException {
        String agentId = "concurrent-agent";
        int threadCount = 10;
        int updatesPerThread = 100;

        Thread[] threads = new Thread[threadCount];
        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < updatesPerThread; j++) {
                    if (j % 3 == 0) {
                        metricsService.recordEvent(agentId, ScannerEventType.CREATION, 0L);
                    } else if (j % 3 == 1) {
                        metricsService.recordEvent(agentId, ScannerEventType.UNCHANGED, 0L);
                    } else {
                        metricsService.recordEmission(agentId);
                    }
                }
            });
            threads[i].start();
        }

        for (Thread t : threads) {
            t.join(10000);
        }

        assertThat(metricsService.getMetrics(agentId).totalDiscovered()).isGreaterThan(0);
    }
}

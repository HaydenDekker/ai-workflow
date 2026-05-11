package com.hdekker.ai_workflow.application.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.hdekker.ai_workflow.application.file.port.FileCounterPort;
import com.hdekker.ai_workflow.domain.pipeline.AgentMetrics;
import com.hdekker.ai_workflow.domain.pipeline.RegexFilterEntry;

/**
 * Unit tests for {@link AgentObserverService}.
 * <p>
 * Tests the pure metrics storage concern — no callbacks, no push.
 * Callback/push tests live in {@link AgentObserverEventBusTest}.
 */
class AgentObserverServiceTest {

    private AgentObserverService service;

    @BeforeEach
    void setUp() {
        service = new AgentObserverService(mock(FileCounterPort.class), "/tmp/test-output");
    }

    // -- dispatch counting tests --

    @Test
    void givenNoDispatches_WhenGetDispatchCount_ThenReturnsZero() {
        assertThat(service.getDispatchCount("agent-1")).isZero();
    }

    @Test
    void givenNoDispatches_WhenGetTotalDispatchCount_ThenReturnsZero() {
        assertThat(service.getTotalDispatchCount()).isZero();
    }

    @Test
    void givenOneDispatch_WhenRecorded_ThenCountIsOne() {
        service.recordDispatch("agent-1", "test.txt");

        assertThat(service.getDispatchCount("agent-1")).isEqualTo(1);
    }

    @Test
    void givenMultipleDispatches_WhenRecorded_ThenCountIncrements() {
        service.recordDispatch("agent-1", "file1.txt");
        service.recordDispatch("agent-1", "file2.txt");
        service.recordDispatch("agent-1", "file3.txt");

        assertThat(service.getDispatchCount("agent-1")).isEqualTo(3);
    }

    @Test
    void givenDispatchForMultipleAgents_WhenGetTotal_ThenReturnsSum() {
        service.recordDispatch("agent-a", "file1.txt");
        service.recordDispatch("agent-a", "file2.txt");
        service.recordDispatch("agent-b", "file3.txt");

        assertThat(service.getTotalDispatchCount()).isEqualTo(3);
    }

    @Test
    void givenDispatchForMultipleAgents_WhenGetPerAgent_ThenReturnsCorrectCount() {
        service.recordDispatch("agent-a", "file1.txt");
        service.recordDispatch("agent-a", "file2.txt");
        service.recordDispatch("agent-b", "file3.txt");

        assertThat(service.getDispatchCount("agent-a")).isEqualTo(2);
        assertThat(service.getDispatchCount("agent-b")).isEqualTo(1);
    }

    // -- storage counting tests --

    @Test
    void givenNoStorages_WhenGetStorageCount_ThenReturnsZero() {
        assertThat(service.getStorageCount("agent-1")).isZero();
    }

    @Test
    void givenNoStorages_WhenGetTotalStorageCount_ThenReturnsZero() {
        assertThat(service.getTotalStorageCount()).isZero();
    }

    @Test
    void givenOneStorage_WhenRecorded_ThenCountIsOne() {
        service.recordStorage("agent-1", "output.txt", Paths.get("/tmp/output.txt"));

        assertThat(service.getStorageCount("agent-1")).isEqualTo(1);
    }

    @Test
    void givenMultipleStorages_WhenRecorded_ThenCountIncrements() {
        service.recordStorage("agent-1", "file1.txt", Paths.get("/tmp/file1.txt"));
        service.recordStorage("agent-1", "file2.txt", Paths.get("/tmp/file2.txt"));
        service.recordStorage("agent-1", "file3.txt", Paths.get("/tmp/file3.txt"));

        assertThat(service.getStorageCount("agent-1")).isEqualTo(3);
    }

    @Test
    void givenStorageForMultipleAgents_WhenGetTotal_ThenReturnsSum() {
        service.recordStorage("agent-a", "file1.txt", Paths.get("/tmp/file1.txt"));
        service.recordStorage("agent-a", "file2.txt", Paths.get("/tmp/file2.txt"));
        service.recordStorage("agent-b", "file3.txt", Paths.get("/tmp/file3.txt"));

        assertThat(service.getTotalStorageCount()).isEqualTo(3);
    }

    @Test
    void givenStorageForMultipleAgents_WhenGetPerAgent_ThenReturnsCorrectCount() {
        service.recordStorage("agent-a", "file1.txt", Paths.get("/tmp/file1.txt"));
        service.recordStorage("agent-a", "file2.txt", Paths.get("/tmp/file2.txt"));
        service.recordStorage("agent-b", "file3.txt", Paths.get("/tmp/file3.txt"));

        assertThat(service.getStorageCount("agent-a")).isEqualTo(2);
        assertThat(service.getStorageCount("agent-b")).isEqualTo(1);
    }

    // -- null path handling --

    @Test
    void givenNullOutputPath_WhenRecorded_ThenStillCounts() {
        service.recordStorage("agent-1", "output.txt", null);

        assertThat(service.getStorageCount("agent-1")).isEqualTo(1);
    }

    // -- independence of dispatch and storage counters --

    @Test
    void givenDispatchAndStorage_WhenRecorded_ThenCountersIndependent() {
        service.recordDispatch("agent-1", "test.txt");
        service.recordStorage("agent-1", "output.txt", Paths.get("/tmp/output.txt"));

        assertThat(service.getDispatchCount("agent-1")).isEqualTo(1);
        assertThat(service.getStorageCount("agent-1")).isEqualTo(1);
        assertThat(service.getTotalDispatchCount()).isEqualTo(1);
        assertThat(service.getTotalStorageCount()).isEqualTo(1);
    }

    // -- concurrency test --

    @Test
    void givenConcurrentAccess_WhenMultipleThreadsUpdate_ThenCountersConsistent()
            throws InterruptedException {
        String agentId = "concurrent-agent";
        int threadCount = 10;
        int updatesPerThread = 100;

        Thread[] threads = new Thread[threadCount];
        for (int i = 0; i < threadCount; i++) {
            final int idx = i;
            threads[i] = new Thread(() -> {
                for (int j = 0; j < updatesPerThread; j++) {
                    if (j % 2 == 0) {
                        service.recordDispatch(agentId, "file-" + idx + "-" + j + ".txt");
                    } else {
                        service.recordStorage(agentId, "output-" + idx + "-" + j + ".txt",
                                Paths.get("/tmp/output-" + idx + "-" + j + ".txt"));
                    }
                }
            });
            threads[i].start();
        }

        for (Thread t : threads) {
            t.join(10000);
        }

        assertThat(service.getDispatchCount(agentId)).isEqualTo(threadCount * updatesPerThread / 2);
        assertThat(service.getStorageCount(agentId)).isEqualTo(threadCount * updatesPerThread / 2);
        assertThat(service.getTotalDispatchCount()).isEqualTo(threadCount * updatesPerThread / 2);
        assertThat(service.getTotalStorageCount()).isEqualTo(threadCount * updatesPerThread / 2);
    }

    // -- output directory file count tests --

    @Test
    void givenNoOutputDirectory_WhenGetOutputDirectoryFileCount_ThenReturnsZero() {
        AgentObserverService serviceWithoutDir = new AgentObserverService(null, null);

        assertThat(serviceWithoutDir.getOutputDirectoryFileCount()).isZero();
    }

    @Test
    void givenOutputDirectoryWithFileCounter_WhenFileCountQueried_ThenDelegatesToFileCounter() {
        var fileCounter = mock(com.hdekker.ai_workflow.application.file.port.FileCounterPort.class);
        when(fileCounter.countFiles(anyString())).thenReturn(42L);

        AgentObserverService serviceWithDir = new AgentObserverService(fileCounter, "/tmp/output");

        long result = serviceWithDir.getOutputDirectoryFileCount();

        assertThat(result).isEqualTo(42L);
    }

    @Test
    void givenOutputDirectoryWithFileCounter_WhenNoFiles_ThenReturnsZero() {
        var fileCounter = mock(com.hdekker.ai_workflow.application.file.port.FileCounterPort.class);
        when(fileCounter.countFiles(anyString())).thenReturn(0L);

        AgentObserverService serviceWithDir = new AgentObserverService(fileCounter, "/tmp/empty-output");

        assertThat(serviceWithDir.getOutputDirectoryFileCount()).isZero();
    }

    @Test
    void givenOutputDirectoryWithFileCounter_WhenMultipleFiles_ThenReturnsCorrectCount() {
        var fileCounter = mock(com.hdekker.ai_workflow.application.file.port.FileCounterPort.class);
        when(fileCounter.countFiles(anyString())).thenReturn(7L);

        AgentObserverService serviceWithDir = new AgentObserverService(fileCounter, "/tmp/output");

        assertThat(serviceWithDir.getOutputDirectoryFileCount()).isEqualTo(7L);
    }

    // -- filter counting tests --

    @Test
    void givenNoFilters_WhenGetFilterCount_ThenReturnsZero() {
        assertThat(service.getFilterCount("agent-1")).isZero();
    }

    @Test
    void givenOneFilter_WhenRecorded_ThenCountIsOne() {
        service.recordFilter("agent-1", "file.txt", ".*\\.java");

        assertThat(service.getFilterCount("agent-1")).isEqualTo(1);
    }

    @Test
    void givenMultipleFilters_WhenRecorded_ThenCountIncrements() {
        service.recordFilter("agent-1", "file1.md", ".*\\.java");
        service.recordFilter("agent-1", "file2.md", ".*\\.java");
        service.recordFilter("agent-1", "file3.md", ".*\\.java");

        assertThat(service.getFilterCount("agent-1")).isEqualTo(3);
    }

    @Test
    void givenMultipleAgents_WhenGetPerAgent_ThenReturnsCorrectCount() {
        service.recordFilter("agent-a", "file1.md", ".*\\.java");
        service.recordFilter("agent-a", "file2.md", ".*\\.java");
        service.recordFilter("agent-b", "file3.md", ".*\\.txt");

        assertThat(service.getFilterCount("agent-a")).isEqualTo(2);
        assertThat(service.getFilterCount("agent-b")).isEqualTo(1);
    }

    @Test
    void givenTotalFilters_WhenGetTotalFilterCount_ThenReturnsSum() {
        service.recordFilter("agent-a", "file1.md", ".*\\.java");
        service.recordFilter("agent-a", "file2.md", ".*\\.java");
        service.recordFilter("agent-b", "file3.md", ".*\\.txt");

        assertThat(service.getTotalFilterCount()).isEqualTo(3);
    }

    // -- ring buffer tests --

    @Test
    void givenTenEntries_WhenRecorded_ThenReturnsAllTen() {
        for (int i = 0; i < 10; i++) {
            service.recordFilter("agent-1", "file" + i + ".md", ".*\\.java");
        }

        List<RegexFilterEntry> entries = service.getLastFilteredEntries("agent-1");
        assertThat(entries).hasSize(10);
        for (int i = 0; i < 10; i++) {
            assertThat(entries.get(i).fileUrl()).isEqualTo("file" + i + ".md");
        }
    }

    @Test
    void givenElevenEntries_WhenRecorded_ThenReturnsLastTen() {
        for (int i = 0; i < 11; i++) {
            service.recordFilter("agent-1", "file" + i + ".md", ".*\\.java");
        }

        List<RegexFilterEntry> entries = service.getLastFilteredEntries("agent-1");
        assertThat(entries).hasSize(10);
        // First entry (file0) should have been evicted
        assertThat(entries.get(0).fileUrl()).isEqualTo("file1.md");
        assertThat(entries.get(9).fileUrl()).isEqualTo("file10.md");
    }

    @Test
    void givenMultipleAgents_WhenGetFilteredEntries_ThenReturnsCorrectAgentEntries() {
        service.recordFilter("agent-a", "file-a1.md", ".*\\.java");
        service.recordFilter("agent-a", "file-a2.md", ".*\\.java");
        service.recordFilter("agent-b", "file-b1.txt", ".*\\.py");

        List<RegexFilterEntry> entriesA = service.getLastFilteredEntries("agent-a");
        List<RegexFilterEntry> entriesB = service.getLastFilteredEntries("agent-b");

        assertThat(entriesA).hasSize(2);
        assertThat(entriesA.get(0).agentId()).isEqualTo("agent-a");
        assertThat(entriesA.get(1).agentId()).isEqualTo("agent-a");

        assertThat(entriesB).hasSize(1);
        assertThat(entriesB.get(0).agentId()).isEqualTo("agent-b");
    }

    // -- AgentMetrics tests --

    @Test
    void givenFreshAgent_WhenGetAgentMetrics_ThenAllZeroAndEmpty() {
        AgentMetrics metrics = service.getAgentMetrics("new-agent");

        assertThat(metrics.dispatchCount()).isZero();
        assertThat(metrics.filterCount()).isZero();
        assertThat(metrics.lastFilteredEntries()).isEmpty();
    }

    @Test
    void givenFiltersAndDispatches_WhenGetAgentMetrics_ThenReturnsAllFields() {
        service.recordDispatch("agent-1", "input.java");
        service.recordDispatch("agent-1", "input2.java");
        service.recordFilter("agent-1", "notes.md", ".*\\.java");

        AgentMetrics metrics = service.getAgentMetrics("agent-1");

        assertThat(metrics.dispatchCount()).isEqualTo(2);
        assertThat(metrics.filterCount()).isEqualTo(1);
        assertThat(metrics.lastFilteredEntries()).hasSize(1);
        assertThat(metrics.lastFilteredEntries().get(0).fileUrl()).isEqualTo("notes.md");
    }

    @Test
    void givenMultipleAgents_WhenGetAgentMetrics_ThenReturnsCorrectAgentData() {
        service.recordDispatch("agent-a", "file1.java");
        service.recordFilter("agent-a", "notes.md", ".*\\.java");
        service.recordFilter("agent-a", "readme.txt", ".*\\.java");
        service.recordDispatch("agent-b", "file1.py");
        service.recordDispatch("agent-b", "file2.py");
        service.recordDispatch("agent-b", "file3.py");

        AgentMetrics metricsA = service.getAgentMetrics("agent-a");
        AgentMetrics metricsB = service.getAgentMetrics("agent-b");

        assertThat(metricsA.dispatchCount()).isEqualTo(1);
        assertThat(metricsA.filterCount()).isEqualTo(2);
        assertThat(metricsA.lastFilteredEntries()).hasSize(2);

        assertThat(metricsB.dispatchCount()).isEqualTo(3);
        assertThat(metricsB.filterCount()).isZero();
        assertThat(metricsB.lastFilteredEntries()).isEmpty();
    }

    // -- filter concurrency test --

    @Test
    void givenConcurrentFilterAccess_WhenMultipleThreadsUpdate_ThenCountersConsistent()
            throws InterruptedException {
        String agentId = "concurrent-filter-agent";
        int threadCount = 10;
        int updatesPerThread = 100;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CopyOnWriteArrayList<Runnable> tasks = new CopyOnWriteArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            final int idx = i;
            tasks.add(() -> {
                for (int j = 0; j < updatesPerThread; j++) {
                    service.recordFilter(agentId,
                            "file-" + idx + "-" + j + ".md", ".*\\.java");
                }
            });
        }

        tasks.forEach(executor::submit);
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        assertThat(service.getFilterCount(agentId)).isEqualTo(threadCount * updatesPerThread);
        assertThat(service.getTotalFilterCount()).isEqualTo(threadCount * updatesPerThread);
        // Ring buffer should cap at 10
        assertThat(service.getLastFilteredEntries(agentId)).hasSize(10);
    }
}

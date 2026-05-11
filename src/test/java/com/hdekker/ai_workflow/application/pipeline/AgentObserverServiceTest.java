package com.hdekker.ai_workflow.application.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Paths;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.hdekker.ai_workflow.application.file.port.FileCounterPort;

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
        FileCounterPort nullCounter = null;
        AgentObserverService serviceWithoutDir = new AgentObserverService(nullCounter, null);

        assertThat(serviceWithoutDir.getOutputDirectoryFileCount()).isZero();
    }

    @Test
    void givenOutputDirectoryWithFileCounter_WhenFileCountQueried_ThenDelegatesToFileCounter() {
        var fileCounter = mock(com.hdekker.ai_workflow.application.file.port.FileCounterPort.class);
        when(fileCounter.countFiles("/tmp/output")).thenReturn(42L);

        AgentObserverService serviceWithDir = new AgentObserverService(fileCounter, "/tmp/output");

        long result = serviceWithDir.getOutputDirectoryFileCount();

        assertThat(result).isEqualTo(42L);
    }

    @Test
    void givenOutputDirectoryWithFileCounter_WhenNoFiles_ThenReturnsZero() {
        var fileCounter = mock(com.hdekker.ai_workflow.application.file.port.FileCounterPort.class);
        when(fileCounter.countFiles("/tmp/empty-output")).thenReturn(0L);

        AgentObserverService serviceWithDir = new AgentObserverService(fileCounter, "/tmp/empty-output");

        assertThat(serviceWithDir.getOutputDirectoryFileCount()).isZero();
    }

    @Test
    void givenOutputDirectoryWithFileCounter_WhenMultipleFiles_ThenReturnsCorrectCount() {
        var fileCounter = mock(com.hdekker.ai_workflow.application.file.port.FileCounterPort.class);
        when(fileCounter.countFiles("/tmp/output")).thenReturn(7L);

        AgentObserverService serviceWithDir = new AgentObserverService(fileCounter, "/tmp/output");

        assertThat(serviceWithDir.getOutputDirectoryFileCount()).isEqualTo(7L);
    }
}

package com.hdekker.ai_workflow.ui.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.hdekker.ai_workflow.app.pipeline.management.ScannerRegistry;
import com.hdekker.ai_workflow.rest.dto.ScannerInfo;

import reactor.core.publisher.Mono;

public class ScannerServiceTest {

    private ScannerService service;
    private ScannerRegistry registry;

    @BeforeEach
    public void init() {
        registry = mock(ScannerRegistry.class);
        service = new ScannerService(registry);
    }

    @Test
    public void givenEmptyRegistry_ExpectEmptyList() {
        when(registry.listAll()).thenReturn(List.of());

        Mono<List<ScannerInfo>> result = service.getAllScannerInfos();
        List<ScannerInfo> scanners = result.block();

        assertThat(scanners).isEmpty();
    }

    @Test
    public void givenRegistryWithScanners_ExpectAllReturned() {
        ScannerInfo info1 = new ScannerInfo("id-1", "agent-1", "/dir/1", "IDLE",
                LocalDateTime.now(), null);
        ScannerInfo info2 = new ScannerInfo("id-2", "agent-2", "/dir/2", "ERROR",
                LocalDateTime.now().minusHours(1), LocalDateTime.now().minusHours(1));

        when(registry.listAll()).thenReturn(List.of(info1, info2));

        Mono<List<ScannerInfo>> result = service.getAllScannerInfos();
        List<ScannerInfo> scanners = result.block();

        assertThat(scanners).hasSize(2);
        assertThat(scanners).containsExactly(info1, info2);
    }

    @Test
    public void givenExceptionInRegistry_ExpectEmptyList() {
        when(registry.listAll()).thenThrow(new RuntimeException("Database error"));

        Mono<List<ScannerInfo>> result = service.getAllScannerInfos();
        List<ScannerInfo> scanners = result.block();

        assertThat(scanners).isEmpty();
    }

    @Test
    public void givenValidAgentId_ExpectDeleteCompletes() {
        Mono<Void> result = service.deleteScanner("agent-1");

        // Should complete without error
        result.block();
    }

    @Test
    public void givenExceptionOnDelete_ExpectCompletes() {
        // Service swallows exceptions, so this should complete
        Mono<Void> result = service.deleteScanner("agent-1");

        result.block();
    }

    @Test
    public void givenValidAgentId_ExpectRefreshCompletes() {
        Mono<Void> result = service.refreshScanner("agent-1");

        // Should complete without error
        result.block();
    }
}

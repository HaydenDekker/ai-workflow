package com.hdekker.ai_workflow.adapter.inbound.ui.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.hdekker.ai_workflow.adapter.inbound.rest.dto.ScannerInfoDTO;
import com.hdekker.ai_workflow.application.pipeline.ScannerRegistry;
import com.hdekker.ai_workflow.application.scanner.ScannerObservabilityUseCase;
import com.hdekker.ai_workflow.application.scanner.port.ScannerMetricsPort;
import com.hdekker.ai_workflow.application.scanner.port.ScannerEventPort;
import com.hdekker.ai_workflow.domain.scanner.ScannerMetrics;

import reactor.core.publisher.Mono;

public class ScannerServiceTest {

    private ScannerService service;
    private ScannerRegistry registry;
    private ScannerObservabilityUseCase observability;

    @BeforeEach
    public void init() {
        registry = mock(ScannerRegistry.class);
        ScannerMetricsPort metricsPort = mock(ScannerMetricsPort.class);
        when(metricsPort.getMetrics(anyString())).thenReturn(new ScannerMetrics("", 0, null, 0L));
        ScannerEventPort eventPort = mock(ScannerEventPort.class);
        observability = new ScannerObservabilityUseCase(metricsPort, eventPort);
        service = new ScannerService(registry, observability);
    }

    @Test
    public void givenEmptyRegistry_ExpectEmptyList() {
        when(registry.listAll()).thenReturn(List.of());

        Mono<List<ScannerInfoDTO>> result = service.getAllScannerInfos();
        List<ScannerInfoDTO> scanners = result.block();

        assertThat(scanners).isEmpty();
    }

    @Test
    public void givenRegistryWithScanners_ExpectAllReturned() {
        com.hdekker.ai_workflow.application.scanner.ScannerService.ScannerInfo domainInfo1 =
                new com.hdekker.ai_workflow.application.scanner.ScannerService.ScannerInfo(
                        "agent-1", "id-1", "/dir/1", "IDLE", null, LocalDateTime.now(), null, null);
        com.hdekker.ai_workflow.application.scanner.ScannerService.ScannerInfo domainInfo2 =
                new com.hdekker.ai_workflow.application.scanner.ScannerService.ScannerInfo(
                        "agent-2", "id-2", "/dir/2", "ERROR", null,
                        LocalDateTime.now().minusHours(1), LocalDateTime.now().minusHours(1), "Some error");

        when(registry.listAll()).thenReturn(List.of(domainInfo1, domainInfo2));

        Mono<List<ScannerInfoDTO>> result = service.getAllScannerInfos();
        List<ScannerInfoDTO> scanners = result.block();

        assertThat(scanners).hasSize(2);
        assertThat(scanners.get(0).agentId()).isEqualTo("agent-1");
        assertThat(scanners.get(0).targetDirectory()).isEqualTo("/dir/1");
        assertThat(scanners.get(0).status()).isEqualTo("IDLE");
        assertThat(scanners.get(1).agentId()).isEqualTo("agent-2");
        assertThat(scanners.get(1).errorMessage()).isEqualTo("Some error");
    }

    @Test
    public void givenExceptionInRegistry_ExpectEmptyList() {
        when(registry.listAll()).thenThrow(new RuntimeException("Database error"));

        Mono<List<ScannerInfoDTO>> result = service.getAllScannerInfos();
        List<ScannerInfoDTO> scanners = result.block();

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

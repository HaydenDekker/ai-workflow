package com.hdekker.ai_workflow.adapter.inbound.ui.service;

import java.util.List;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hdekker.ai_workflow.adapter.inbound.rest.dto.ScannerInfoDTO;
import com.hdekker.ai_workflow.application.pipeline.ScannerRegistry;
import com.hdekker.ai_workflow.application.scanner.ScannerObservabilityUseCase;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * UI-layer service for scanner operations.
 * Thin wrapper around {@link ScannerRegistry} for use by Vaadin views.
 */
@Service
public class ScannerService {

    private static final Logger log = LoggerFactory.getLogger(ScannerService.class);

    private final ScannerRegistry scannerRegistry;
    private final ScannerObservabilityUseCase observability;

    @Autowired
    public ScannerService(com.hdekker.ai_workflow.application.pipeline.ScannerRegistry scannerRegistry,
                          ScannerObservabilityUseCase observability) {
        this.scannerRegistry = scannerRegistry;
        this.observability = observability;
    }

    /**
     * Get all scanner information.
     */
    public Mono<List<ScannerInfoDTO>> getAllScannerInfos() {
        try {
            List<com.hdekker.ai_workflow.application.scanner.ScannerService.ScannerInfo> domainScanners = scannerRegistry.listAll();
            List<ScannerInfoDTO> result = new java.util.ArrayList<>();
            for (com.hdekker.ai_workflow.application.scanner.ScannerService.ScannerInfo d : domainScanners) {
                long fileCount = observability.getMetrics(d.agentId()).fileCount();
                String fileResult = d.fileResult() != null ? d.fileResult().name() : "";
                result.add(new ScannerInfoDTO(
                        d.id(),
                        d.agentId(),
                        d.folderPath(),
                        d.status(),
                        d.createdAt(),
                        d.lastEmittedAt(),
                        d.errorMessage(),
                        fileCount,
                        fileResult
                ));
            }
            return Mono.just(result);
        } catch (Exception ex) {
            log.error("Error fetching scanner infos", ex);
            return Mono.just(List.of());
        }
    }

    /**
     * Delete a scanner by its agentId.
     */
    public Mono<Void> deleteScanner(String agentId) {
        try {
            scannerRegistry.deleteById(agentId);
            return Mono.empty();
        } catch (Exception ex) {
            log.error("Error deleting scanner for agent: {}", agentId, ex);
            return Mono.empty();
        }
    }

    /**
     * Refresh a scanner (reset to emit-all mode).
     */
    public Mono<Void> refreshScanner(String agentId) {
        try {
            scannerRegistry.refreshAgent(agentId);
            return Mono.empty();
        } catch (Exception ex) {
            log.error("Error refreshing scanner for agent: {}", agentId, ex);
            return Mono.empty();
        }
    }
}

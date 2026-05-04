package com.hdekker.ai_workflow.adapter.inbound.ui.service;

import java.util.List;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hdekker.ai_workflow.adapter.inbound.rest.dto.ScannerInfo;
import com.hdekker.ai_workflow.app.pipeline.management.ScannerRegistry;

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

    @Autowired
    public ScannerService(ScannerRegistry scannerRegistry) {
        this.scannerRegistry = scannerRegistry;
    }

    /**
     * Get all scanner information.
     */
    public Mono<List<ScannerInfo>> getAllScannerInfos() {
        try {
            List<ScannerInfo> scanners = scannerRegistry.listAll();
            return Mono.just(scanners);
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

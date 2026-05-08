package com.hdekker.ai_workflow.adapter.inbound.rest.controller;

import java.util.List;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hdekker.ai_workflow.adapter.inbound.rest.dto.ScannerInfoDTO;
import com.hdekker.ai_workflow.application.pipeline.ScannerRegistry;
import com.hdekker.ai_workflow.application.scanner.ScannerObservabilityUseCase;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for scanner management.
 *
 * Scanners are created implicitly when an agent is created (POST /api/agents
 * with targetDirectory triggers scanner creation). This controller exposes
 * read and delete endpoints only.
 */
@RestController
@RequestMapping("/api/scanners")
public class ScannerController {

    private static final Logger log = LoggerFactory.getLogger(ScannerController.class);

    private final ScannerRegistry scannerRegistry;
    private final ScannerObservabilityUseCase observability;

    @Autowired
    public ScannerController(com.hdekker.ai_workflow.application.pipeline.ScannerRegistry scannerRegistry,
                           ScannerObservabilityUseCase observability) {
        this.scannerRegistry = scannerRegistry;
        this.observability = observability;
    }

    /**
     * List all scanners.
     */
    @GetMapping
    public ResponseEntity<List<ScannerInfoDTO>> listScanners() {
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
        log.debug("Listed {} scanners", result.size());
        return ResponseEntity.ok(result);
    }

    /**
     * Delete a scanner by its ID (via agentId).
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteScanner(@PathVariable String id) {
        log.info("Deleting scanner with id/agentId: {}", id);
        scannerRegistry.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}

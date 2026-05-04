package com.hdekker.ai_workflow.adapter.inbound.rest.controller;

import java.util.List;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hdekker.ai_workflow.app.pipeline.management.ScannerRegistry;
import com.hdekker.ai_workflow.adapter.inbound.rest.dto.ScannerInfo;

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

    @Autowired
    public ScannerController(ScannerRegistry scannerRegistry) {
        this.scannerRegistry = scannerRegistry;
    }

    /**
     * List all scanners.
     */
    @GetMapping
    public ResponseEntity<List<ScannerInfo>> listScanners() {
        List<ScannerInfo> scanners = scannerRegistry.listAll();
        log.debug("Listed {} scanners", scanners.size());
        return ResponseEntity.ok(scanners);
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

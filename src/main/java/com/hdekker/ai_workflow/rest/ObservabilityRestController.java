package com.hdekker.ai_workflow.rest;

import java.util.List;

import com.hdekker.ai_workflow.rest.dto.LLMStatus;
import com.hdekker.ai_workflow.usecases.AgentStatusUsecase;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoint for observability data.
 *
 * Endpoints:
 * - GET  /api/observability/llm-status  - Get current status
 * - POST /api/observability/llm-status/poll - Trigger immediate poll
 */
@RestController
@RequestMapping("/api/observability")
public class ObservabilityRestController {

    @Autowired
    private AgentStatusUsecase llmStatusService;

    /**
     * Get current LLM status from database cache.
     * Fast - no polling, just reads from database.
     */
    @GetMapping("/llm-status")
    public ResponseEntity<List<LLMStatus>> getLLMStatus() {
        List<LLMStatus> status = llmStatusService.getCurrentStatus();
        return ResponseEntity.ok(status);
    }

    /**
     * Trigger immediate polling.
     * Returns updated status after polling completes.
     */
    @PostMapping("/llm-status/poll")
    public ResponseEntity<List<LLMStatus>> triggerPoll() {
        List<LLMStatus> status = llmStatusService.triggerPoll();
        return ResponseEntity.ok(status);
    }
}

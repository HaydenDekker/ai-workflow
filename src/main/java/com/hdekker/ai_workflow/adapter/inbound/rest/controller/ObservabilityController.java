package com.hdekker.ai_workflow.adapter.inbound.rest.controller;

import java.util.List;

import com.hdekker.ai_workflow.adapter.inbound.rest.dto.AdapterStatusDTO;
import com.hdekker.ai_workflow.adapter.inbound.rest.dto.LLMStatusDTO;
import com.hdekker.ai_workflow.application.agent.AgentStatusService;
import com.hdekker.ai_workflow.application.agent.port.LLMHealthPort;
import com.hdekker.ai_workflow.application.agent.port.LLMStatusRepository.LLMStatusRecord;

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
public class ObservabilityController {

    @Autowired
    private AgentStatusService agentStatusService;

    /**
     * Get current LLM status from database cache.
     * Fast - no polling, just reads from database.
     */
    @GetMapping("/llm-status")
    public ResponseEntity<List<LLMStatusDTO>> getLLMStatus() {
        List<LLMStatusRecord> records = agentStatusService.getCurrentStatus();
        List<LLMStatusDTO> result = new java.util.ArrayList<>();
        for (LLMStatusRecord r : records) {
            result.add(new LLMStatusDTO(
                    r.endpoint(),
                    r.configuredModel(),
                    AdapterStatusDTO.valueOf(r.status()),
                    r.lastChecked(),
                    r.modelCount(),
                    r.modelNames() != null && !r.modelNames().isEmpty()
                            ? List.of(r.modelNames().split(","))
                            : List.of(),
                    r.errorMessage()
            ));
        }
        return ResponseEntity.ok(result);
    }

    /**
     * Trigger immediate polling.
     * Returns updated status after polling completes.
     */
    @PostMapping("/llm-status/poll")
    public ResponseEntity<List<LLMStatusDTO>> triggerPoll() {
        List<LLMHealthPort.LLMStatus> portStatuses = agentStatusService.triggerPoll();
        List<LLMStatusDTO> result = new java.util.ArrayList<>();
        for (LLMHealthPort.LLMStatus s : portStatuses) {
            AdapterStatusDTO dtoStatus =
                    switch (s.status()) {
                        case UP -> AdapterStatusDTO.UP;
                        case DOWN -> AdapterStatusDTO.DOWN;
                        case WARN -> AdapterStatusDTO.WARN;
                    };
            result.add(new LLMStatusDTO(
                    s.endpoint(),
                    s.configuredModel(),
                    dtoStatus,
                    s.lastChecked(),
                    s.modelCount(),
                    s.modelNames(),
                    s.errorMessage()
            ));
        }
        return ResponseEntity.ok(result);
    }
}

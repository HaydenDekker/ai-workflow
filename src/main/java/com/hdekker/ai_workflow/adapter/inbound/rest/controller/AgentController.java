package com.hdekker.ai_workflow.adapter.inbound.rest.controller;

import java.util.List;
import java.util.Map;

import com.hdekker.ai_workflow.adapter.inbound.rest.dto.AgentInfoDTO;
import com.hdekker.ai_workflow.application.agent.AgentLifecycleService;
import com.hdekker.ai_workflow.application.agent.port.DirectoryValidationPort;
import com.hdekker.ai_workflow.application.agent.port.DirectoryValidationPort.ValidationResult;
import com.hdekker.ai_workflow.application.pipeline.AgentObserverUseCase;
import com.hdekker.ai_workflow.domain.agent.AgentDefinition;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agents")
public class AgentController {

    @Autowired
    private AgentLifecycleService agentLifecycleService;

    @Autowired
    private DirectoryValidationPort directoryValidationPort;

    @Autowired(required = false)
    private AgentObserverUseCase agentObserverUseCase;

    @PostMapping
    public ResponseEntity<?> createAgent(@RequestBody AgentDefinition agentDefinition) {
        String targetDir = agentDefinition.targetDirectory();
        ValidationResult result = directoryValidationPort.validate(targetDir);
        if (!result.valid()) {
            return ResponseEntity.badRequest().body(java.util.Map.of("error", result.reason()));
        }
        com.hdekker.ai_workflow.domain.agent.AgentInfo domainInfo = agentLifecycleService.addDynamicAgent(agentDefinition, targetDir);
        return ResponseEntity.ok(new AgentInfoDTO(domainInfo.id(), domainInfo.definition(),
                domainInfo.createdAt(), domainInfo.active(), domainInfo.source()));
    }

    @GetMapping
    public ResponseEntity<List<AgentInfoDTO>> listAgents() {
        List<com.hdekker.ai_workflow.domain.agent.AgentInfo> domainAgents = agentLifecycleService.listAgents();
        List<AgentInfoDTO> result = new java.util.ArrayList<>();
        for (com.hdekker.ai_workflow.domain.agent.AgentInfo d : domainAgents) {
            result.add(new AgentInfoDTO(d.id(), d.definition(), d.createdAt(), d.active(), d.source()));
        }
        return ResponseEntity.ok(result);
    }

    /**
     * Update an agent: remove the existing agent (including scanner) and re-add
     * with the updated definition.
     */
    @PutMapping("/{id}")
    public ResponseEntity<AgentInfoDTO> updateAgent(
            @PathVariable String id,
            @RequestBody AgentDefinition agentDefinition) {
        com.hdekker.ai_workflow.domain.agent.AgentInfo domainInfo = agentLifecycleService.updateAgent(id, agentDefinition);
        if (domainInfo != null) {
            return ResponseEntity.ok(new AgentInfoDTO(domainInfo.id(), domainInfo.definition(),
                    domainInfo.createdAt(), domainInfo.active(), domainInfo.source()));
        }
        return ResponseEntity.notFound().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAgent(@PathVariable String id) {
        agentLifecycleService.removeAgent(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/enable")
    public ResponseEntity<AgentInfoDTO> enableAgent(@PathVariable String id) {
        com.hdekker.ai_workflow.domain.agent.AgentInfo domainInfo = agentLifecycleService.enableAgent(id);
        if (domainInfo != null) {
            return ResponseEntity.ok(new AgentInfoDTO(domainInfo.id(), domainInfo.definition(),
                    domainInfo.createdAt(), domainInfo.active(), domainInfo.source()));
        }
        return ResponseEntity.notFound().build();
    }

    @PutMapping("/{id}/disable")
    public ResponseEntity<Void> disableAgent(@PathVariable String id) {
        agentLifecycleService.disableAgent(id);
        return ResponseEntity.ok().build();
    }

    /**
     * Refresh an agent: trigger full rescan of its target directory.
     * Used when an agent's definition is modified and needs reprocessing.
     */
    @PostMapping("/{id}/refresh")
    public ResponseEntity<AgentInfoDTO> refreshAgent(@PathVariable String id) {
        com.hdekker.ai_workflow.domain.agent.AgentInfo domainInfo = agentLifecycleService.refreshAgent(id);
        if (domainInfo != null) {
            return ResponseEntity.ok(new AgentInfoDTO(domainInfo.id(), domainInfo.definition(),
                    domainInfo.createdAt(), domainInfo.active(), domainInfo.source()));
        }
        return ResponseEntity.notFound().build();
    }

    /**
     * Get the number of files in the output directory.
     */
    @GetMapping("/output-file-count")
    public ResponseEntity<Map<String, Long>> getOutputDirectoryFileCount() {
        long count = agentObserverUseCase != null
                ? agentObserverUseCase.getOutputDirectoryFileCount()
                : 0L;
        return ResponseEntity.ok(Map.of("outputDirectoryFileCount", count));
    }

    /**
     * Get the total dispatch count across all agents.
     */
    @GetMapping("/dispatch-count")
    public ResponseEntity<Map<String, Long>> getDispatchCount() {
        long count = agentObserverUseCase != null
                ? agentObserverUseCase.getTotalDispatchCount()
                : 0L;
        return ResponseEntity.ok(Map.of("totalDispatchCount", count));
    }
}

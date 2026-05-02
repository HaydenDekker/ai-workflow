package com.hdekker.ai_workflow.rest;

import java.util.List;

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

import com.hdekker.ai_workflow.usecases.AgentLifecycleUseCase;
import com.hdekker.ai_workflow.files.TargetDirectoryValidator;
import com.hdekker.ai_workflow.files.TargetDirectoryValidator.ValidationResult;
import com.hdekker.ai_workflow.domain.agent.AgentDefinition;
import com.hdekker.ai_workflow.rest.dto.AgentInfo;

@RestController
@RequestMapping("/api/agents")
public class AgentRestController {

    @Autowired
    private AgentLifecycleUseCase dynamicAgentManager;

    @Autowired
    private TargetDirectoryValidator targetDirectoryValidator;

    @PostMapping
    public ResponseEntity<?> createAgent(@RequestBody AgentDefinition agentDefinition) {
        String targetDir = agentDefinition.targetDirectory();
        ValidationResult result = targetDirectoryValidator.validate(targetDir);
        if (!result.valid()) {
            return ResponseEntity.badRequest().body(java.util.Map.of("error", result.reason()));
        }
        AgentInfo agentInfo = dynamicAgentManager.addDynamicAgent(agentDefinition, targetDir);
        return ResponseEntity.ok(agentInfo);
    }

    @GetMapping
    public ResponseEntity<List<AgentInfo>> listAgents() {
        List<AgentInfo> agents = dynamicAgentManager.listAgents();
        return ResponseEntity.ok(agents);
    }

    /**
     * Update an agent: remove the existing agent (including scanner) and re-add
     * with the updated definition.
     */
    @PutMapping("/{id}")
    public ResponseEntity<AgentInfo> updateAgent(
            @PathVariable String id,
            @RequestBody AgentDefinition agentDefinition) {
        AgentInfo agentInfo = dynamicAgentManager.updateAgent(id, agentDefinition);
        if (agentInfo != null) {
            return ResponseEntity.ok(agentInfo);
        }
        return ResponseEntity.notFound().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAgent(@PathVariable String id) {
        dynamicAgentManager.removeAgent(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/enable")
    public ResponseEntity<AgentInfo> enableAgent(@PathVariable String id) {
        AgentInfo agentInfo = dynamicAgentManager.enableAgent(id);
        if (agentInfo != null) {
            return ResponseEntity.ok(agentInfo);
        }
        return ResponseEntity.notFound().build();
    }

    @PutMapping("/{id}/disable")
    public ResponseEntity<Void> disableAgent(@PathVariable String id) {
        dynamicAgentManager.disableAgent(id);
        return ResponseEntity.ok().build();
    }

    /**
     * Refresh an agent: trigger full rescan of its target directory.
     * Used when an agent's definition is modified and needs reprocessing.
     */
    @PostMapping("/{id}/refresh")
    public ResponseEntity<AgentInfo> refreshAgent(@PathVariable String id) {
        AgentInfo agentInfo = dynamicAgentManager.refreshAgent(id);
        if (agentInfo != null) {
            return ResponseEntity.ok(agentInfo);
        }
        return ResponseEntity.notFound().build();
    }
}

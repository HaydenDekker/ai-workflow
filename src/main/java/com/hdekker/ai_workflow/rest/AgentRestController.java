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

import com.hdekker.ai_workflow.app.pipeline.management.DynamicAgentManager;
import com.hdekker.ai_workflow.pipeline.domain.AgentDefinition;
import com.hdekker.ai_workflow.rest.dto.AgentInfo;

@RestController
@RequestMapping("/api/agents")
public class AgentRestController {

    @Autowired
    private DynamicAgentManager dynamicAgentManager;

    @PostMapping
    public ResponseEntity<AgentInfo> createAgent(@RequestBody AgentDefinition agentDefinition) {
        AgentInfo agentInfo = dynamicAgentManager.addDynamicAgent(agentDefinition);
        return ResponseEntity.ok(agentInfo);
    }

    @GetMapping
    public ResponseEntity<List<AgentInfo>> listAgents() {
        List<AgentInfo> agents = dynamicAgentManager.listAgents();
        return ResponseEntity.ok(agents);
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
}

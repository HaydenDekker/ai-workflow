package com.hdekker.ai_workflow.rest;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hdekker.ai_workflow.app.pipeline.management.DynamicPipelineManager;
import com.hdekker.ai_workflow.pipeline.domain.AgentDefinition;
import com.hdekker.ai_workflow.rest.dto.AgentInfo;

@RestController
@RequestMapping("/api/agents")
public class AgentRestController {

    @Autowired
    private DynamicPipelineManager dynamicPipelineManager;

    @PostMapping
    public ResponseEntity<AgentInfo> createAgent(@RequestBody AgentDefinition agentDefinition) {
        AgentInfo agentInfo = dynamicPipelineManager.addDynamicPipeline(agentDefinition);
        return ResponseEntity.ok(agentInfo);
    }

    @GetMapping
    public ResponseEntity<List<AgentInfo>> listAgents() {
        List<AgentInfo> agents = dynamicPipelineManager.listPipelines();
        return ResponseEntity.ok(agents);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAgent(@PathVariable String id) {
        dynamicPipelineManager.removePipeline(id);
        return ResponseEntity.noContent().build();
    }
}

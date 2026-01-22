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

import com.hdekker.ai_workflow.pipeline.domain.AgentDefinition;
import com.hdekker.ai_workflow.pipeline.management.DynamicPipelineManager;
import com.hdekker.ai_workflow.rest.dto.PipelineInfo;

@RestController
@RequestMapping("/api/pipelines")
public class PipelineRestController {

    @Autowired
    private DynamicPipelineManager dynamicPipelineManager;

    @PostMapping
    public ResponseEntity<PipelineInfo> createPipeline(@RequestBody AgentDefinition agentDefinition) {
        PipelineInfo pipelineInfo = dynamicPipelineManager.addDynamicPipeline(agentDefinition);
        return ResponseEntity.ok(pipelineInfo);
    }

    @GetMapping
    public ResponseEntity<List<PipelineInfo>> listPipelines() {
        List<PipelineInfo> pipelines = dynamicPipelineManager.listPipelines();
        return ResponseEntity.ok(pipelines);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePipeline(@PathVariable String id) {
        dynamicPipelineManager.removePipeline(id);
        return ResponseEntity.noContent().build();
    }
}
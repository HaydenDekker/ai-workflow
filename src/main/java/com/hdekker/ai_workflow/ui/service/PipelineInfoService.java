package com.hdekker.ai_workflow.ui.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import reactor.core.publisher.Mono;

import com.hdekker.ai_workflow.app.pipeline.management.DynamicPipelineManager;
import com.hdekker.ai_workflow.rest.dto.PipelineInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class PipelineInfoService {

    private static final Logger log = LoggerFactory.getLogger(PipelineInfoService.class);

    private final DynamicPipelineManager dynamicPipelineManager;

    @Autowired
    public PipelineInfoService(DynamicPipelineManager dynamicPipelineManager) {
        this.dynamicPipelineManager = dynamicPipelineManager;
    }

    public Mono<List<PipelineInfo>> getAllPipelineInfos() {
        try {
            List<PipelineInfo> pipelines = dynamicPipelineManager.listPipelines();
            return Mono.just(pipelines);
        } catch (Exception ex) {
            log.error("Error fetching pipeline infos", ex);
            return Mono.just(List.of());
        }
    }

    public Mono<Void> deletePipeline(String id) {
        try {
            dynamicPipelineManager.removePipeline(id);
            return Mono.empty();
        } catch (Exception ex) {
            log.error("Error deleting pipeline with id: {}", id, ex);
            return Mono.empty();
        }
    }
}
package com.hdekker.ai_workflow.app.pipeline.management;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import com.hdekker.ai_workflow.app.pipeline.AgentConfigurator;
import com.hdekker.ai_workflow.files.FileScanner;
import com.hdekker.ai_workflow.files.FileWriter;
import com.hdekker.ai_workflow.pipeline.domain.AgentDefinition;
import com.hdekker.ai_workflow.prompt.PromptResponse;
import com.hdekker.ai_workflow.rest.dto.PipelineInfo;

import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import java.nio.file.Path;

public class DynamicPipelineManager {

    Logger log = LoggerFactory.getLogger(DynamicPipelineManager.class);

    private final Map<String, PipelineRegistryEntry> pipelineRegistry = new ConcurrentHashMap<>();

    private final AgentConfigurator agentConfigurator;

    // Constructor with abstractions
    public DynamicPipelineManager(
            FileScanner fileScanner,
            FileWriter fileWriter,
            Path outputDirectory,
            ChatClient chatClient) {
        this.agentConfigurator = new AgentConfigurator(
                fileScanner.flux(),
                chatClient,
                fileWriter.createPersister(outputDirectory));
    }

    public void initializeFromYAML(List<AgentDefinition> yamlAgents) {
        yamlAgents.forEach(agent -> {
           
        	Flux<PromptResponse> flux = agentConfigurator.configure(agent);
            
            Disposable subscription = flux.subscribe();
            PipelineRegistryEntry entry = new PipelineRegistryEntry(
                agent.title(), // Use title as ID for YAML pipelines
                agent,
                flux,
                LocalDateTime.now(),
                "YAML",
                subscription
            );
            pipelineRegistry.put(agent.title(), entry);
            log.info("Initialized YAML pipeline: {}", agent.title());
            
        });
    }

    public PipelineInfo addDynamicPipeline(AgentDefinition def) {
        String id = UUID.randomUUID().toString();
        Flux<PromptResponse> flux = agentConfigurator.configure(def);
        Disposable subscription = flux.subscribe();

        PipelineRegistryEntry entry = new PipelineRegistryEntry(
            id,
            def,
            flux,
            LocalDateTime.now(),
            "DYNAMIC",
            subscription
        );

        pipelineRegistry.put(id, entry);
        log.info("Added dynamic pipeline: {}", id);

        return new PipelineInfo(id, def, entry.createdAt(), true, "DYNAMIC");
    }

    public void removePipeline(String id) {
        PipelineRegistryEntry entry = pipelineRegistry.remove(id);
        if (entry != null) {
            entry.subscription().dispose();
            log.info("Removed pipeline: {}", id);
        } else {
            log.warn("Pipeline not found for removal: {}", id);
        }
    }

    public List<PipelineInfo> listPipelines() {
        return pipelineRegistry.values().stream()
            .map(entry -> new PipelineInfo(
                entry.id(),
                entry.agentDefinition(),
                entry.createdAt(),
                true, // active as long as in registry
                entry.source()
            ))
            .toList();
    }

    private record PipelineRegistryEntry(
        String id,
        AgentDefinition agentDefinition,
        Flux<PromptResponse> flux,
        LocalDateTime createdAt,
        String source,  // "YAML" or "DYNAMIC"
        Disposable subscription
    ) {}
}
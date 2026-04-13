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
import com.hdekker.ai_workflow.rest.dto.AgentInfo;

import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import java.nio.file.Path;

public class DynamicAgentManager {

    Logger log = LoggerFactory.getLogger(DynamicAgentManager.class);

    private final Map<String, AgentRegistryEntry> agentRegistry = new ConcurrentHashMap<>();

    private final AgentConfigurator agentConfigurator;

    // Constructor with abstractions
    public DynamicAgentManager(
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
            AgentRegistryEntry entry = new AgentRegistryEntry(
                agent.title(), // Use title as ID for YAML agents
                agent,
                flux,
                LocalDateTime.now(),
                "YAML",
                subscription
            );
            agentRegistry.put(agent.title(), entry);
            log.info("Initialized YAML agent: {}", agent.title());
            
        });
    }

    public AgentInfo addDynamicAgent(AgentDefinition def) {
        String id = UUID.randomUUID().toString();
        Flux<PromptResponse> flux = agentConfigurator.configure(def);
        Disposable subscription = flux.subscribe();

        AgentRegistryEntry entry = new AgentRegistryEntry(
            id,
            def,
            flux,
            LocalDateTime.now(),
            "DYNAMIC",
            subscription
        );

        agentRegistry.put(id, entry);
        log.info("Added dynamic agent: {}", id);

        return new AgentInfo(id, def, entry.createdAt(), true, "DYNAMIC");
    }

    public void removeAgent(String id) {
        AgentRegistryEntry entry = agentRegistry.remove(id);
        if (entry != null) {
            entry.subscription().dispose();
            log.info("Removed agent: {}", id);
        } else {
            log.warn("Agent not found for removal: {}", id);
        }
    }

    public List<AgentInfo> listAgents() {
        return agentRegistry.values().stream()
            .map(entry -> new AgentInfo(
                entry.id(),
                entry.agentDefinition(),
                entry.createdAt(),
                true, // active as long as in registry
                entry.source()
            ))
            .toList();
    }

    private record AgentRegistryEntry(
        String id,
        AgentDefinition agentDefinition,
        Flux<PromptResponse> flux,
        LocalDateTime createdAt,
        String source,  // "YAML" or "DYNAMIC"
        Disposable subscription
    ) {}
}
package com.hdekker.ai_workflow.usecases;

import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;

import com.hdekker.ai_workflow.app.pipeline.AgentConfigurator;
import com.hdekker.ai_workflow.app.pipeline.management.ScannerRegistry;
import com.hdekker.ai_workflow.database.agent.AgentEntity;
import com.hdekker.ai_workflow.database.agent.AgentPersistenceUsecase;
import com.hdekker.ai_workflow.files.FileHistory;
import com.hdekker.ai_workflow.files.FileWriter;
import com.hdekker.ai_workflow.pipeline.domain.AgentDefinition;
import com.hdekker.ai_workflow.prompt.PromptResponse;
import com.hdekker.ai_workflow.rest.dto.AgentInfo;
import com.hdekker.ai_workflow.rest.dto.ScannerInfo;

import reactor.core.Disposable;
import reactor.core.publisher.Flux;

/**
 * Orchestrates the lifecycle of dynamic agents and their associated scanners.
 * <p>
 * Each dynamic agent gets its own scanner (one-to-one relationship).
 * YAML agents share a default scanner created from the application config.
 * <p>
 * Scanner lifecycle:
 * <ul>
 *   <li>Created when an agent is added (via {@link #addDynamicAgent(AgentDefinition, String)})</li>
 *   <li>Destroyed when the agent is removed (via {@link #removeAgent(String)})</li>
 *   <li>Reset to full-scan mode when an agent is refreshed (via {@link #refreshAgent(String)})</li>
 * </ul>
 */
public class AgentLifecycleUseCase {

    private static final Logger log = LoggerFactory.getLogger(AgentLifecycleUseCase.class);

    private final Map<String, AgentRegistryEntry> agentRegistry = new ConcurrentHashMap<>();
    private final Map<String, DormantAgentEntry> dormantAgents = new ConcurrentHashMap<>();

    private final ScannerRegistry scannerRegistry;
    private final FileWriter fileWriter;
    private final Path outputDirectory;
    private final ChatClient chatClient;
    private final AgentPersistenceUsecase persistenceService;

    /**
     * Default constructor for use outside Spring (no persistence, no scanner registry).
     * Agents use a shared empty flux — suitable for unit tests only.
     */
    public AgentLifecycleUseCase() {
        this.scannerRegistry = null;
        this.fileWriter = null;
        this.outputDirectory = null;
        this.chatClient = null;
        this.persistenceService = null;
    }

    /**
     * Full constructor with Spring-managed dependencies.
     *
     * @param scannerRegistry     registry for per-agent scanner instances
     * @param fileWriter          file writer abstraction
     * @param outputDirectory     default output directory
     * @param chatClient          LLM chat client
     * @param persistenceService  agent persistence usecase (may be null)
     */
    public AgentLifecycleUseCase(ScannerRegistry scannerRegistry,
                                 FileWriter fileWriter,
                                 Path outputDirectory,
                                 ChatClient chatClient,
                                 AgentPersistenceUsecase persistenceService) {
        this.scannerRegistry = scannerRegistry;
        this.fileWriter = fileWriter;
        this.outputDirectory = outputDirectory;
        this.chatClient = chatClient;
        this.persistenceService = persistenceService;
    }

    /**
     * Initialize agents from YAML configuration.
     * YAML agents share a default scanner (if scannerRegistry is available).
     * Persists each agent to the database with active=true.
     */
    public void initializeFromYAML(List<AgentDefinition> yamlAgents) {
        yamlAgents.forEach(agent -> {
            String id = agent.title(); // Use title as ID for YAML agents
            String targetDir = agent.targetDirectory() != null ? agent.targetDirectory() : "/tmp";
            Flux<PromptResponse> flux = buildFlux(agent, targetDir);
            Disposable subscription = flux.subscribe();

            String scannerId = null;
            if (scannerRegistry != null) {
                ScannerInfo scannerInfo = scannerRegistry.createForAgent(id, targetDir, 5);
                scannerId = scannerInfo.id();
            }

            AgentRegistryEntry entry = new AgentRegistryEntry(id, agent, flux, LocalDateTime.now(), "YAML",
                    subscription, scannerId);
            agentRegistry.put(id, entry);

            // Persist to DB if persistence service is available
            if (persistenceService != null) {
                persistenceService.save(id, agent, "YAML", scannerId);
            }

            log.info("Initialized YAML agent: {} (scannerId: {})", id, scannerId);
        });
    }

    /**
     * Restore agents from the database on startup.
     * Active agents get flux/subscription AND a new scanner created in the ScannerRegistry.
     * Disabled agents stay dormant (no scanner created).
     * <p>
     * Scanners are recreated on restore because the previous {@code FileSystemScannerAdapter}
     * instances were ephemeral (Spring singleton scope destroyed on restart). The scannerId
     * stored in the DB is the old ID — we create a fresh scanner and update the entity.
     */
    public void restoreFromDatabase() {
        if (persistenceService == null) {
            return;
        }

        // Restore active (enabled) agents — creates scanners for each
        List<AgentEntity> activeEntities = persistenceService.findAllActive();
        int restoredCount = 0;
        for (AgentEntity entity : activeEntities) {
            try {
                Optional<AgentDefinition> definitionOpt = persistenceService.getDefinition(entity.getId());
                if (definitionOpt.isPresent()) {
                    AgentDefinition def = definitionOpt.get();
                    String targetDir = def.targetDirectory() != null ? def.targetDirectory() : "/tmp";

                    // Create a new scanner for this restored agent (one-to-one)
                    String scannerId = null;
                    if (scannerRegistry != null) {
                        ScannerInfo scannerInfo = scannerRegistry.createForAgent(entity.getId(), targetDir, 5);
                        scannerId = scannerInfo.id();
                        // Update the entity in DB with the new scannerId
                        persistenceService.save(entity.getId(), def, entity.getSource(), scannerId);
                    }

                    Flux<PromptResponse> flux = buildFlux(def, targetDir);
                    Disposable subscription = flux.subscribe();

                    AgentRegistryEntry entry = new AgentRegistryEntry(entity.getId(), def, flux, entity.getCreatedAt(),
                            entity.getSource(), subscription, scannerId);
                    agentRegistry.put(entity.getId(), entry);
                    restoredCount++;
                    log.info("Restored active agent from DB: {} (source: {}, scannerId: {})",
                            entity.getId(), entity.getSource(), scannerId);
                }
            } catch (Exception e) {
                log.error("Failed to restore agent from DB: {}", entity.getId(), e);
            }
        }

        // Load disabled agents as dormant — no scanner created for dormant agents
        List<AgentEntity> dormantEntities = persistenceService.findAllOrdered();
        int dormantCount = 0;
        for (AgentEntity entity : dormantEntities) {
            if (agentRegistry.containsKey(entity.getId())) {
                continue; // Already restored as active
            }
            try {
                Optional<AgentDefinition> definitionOpt = persistenceService.getDefinition(entity.getId());
                if (definitionOpt.isPresent()) {
                    DormantAgentEntry dormantEntry = new DormantAgentEntry(entity.getId(), definitionOpt.get(),
                            entity.getCreatedAt(), entity.getSource(), entity.getScannerId());
                    dormantAgents.put(entity.getId(), dormantEntry);
                    dormantCount++;
                    log.info("Loaded dormant agent from DB: {} (source: {}, scannerId: {})",
                            entity.getId(), entity.getSource(), entity.getScannerId());
                }
            } catch (Exception e) {
                log.error("Failed to load dormant agent from DB: {}", entity.getId(), e);
            }
        }

        log.info("Restored {} active agents and {} dormant agents from database", restoredCount, dormantCount);
    }

    /**
     * Build a Flux for an agent's processing pipeline.
     * Uses the scanner registry to get the agent's scanner flux if available.
     */
    private Flux<PromptResponse> buildFlux(AgentDefinition def, String targetDir) {
        if (scannerRegistry != null) {
            // Get the scanner's flux for this agent
            Flux<FileHistory> scannerFlux = scannerRegistry.getScannerFlux(def.title());
            return new AgentConfigurator(
                    scannerFlux,
                    chatClient,
                    fileWriter.createPersister(outputDirectory))
                    .configure(def);
        } else {
            // No scanner registry — use empty flux
            return new AgentConfigurator(
                    Flux.empty(),
                    chatClient,
                    fileWriter.createPersister(outputDirectory))
                    .configure(def);
        }
    }

    /**
     * Add a new dynamic agent with its target directory.
     * Creates a dedicated scanner for this agent (one-to-one relationship).
     * Persists to DB with active=true.
     *
     * @param def              the agent definition
     * @param targetDirectory  the directory to scan (must be absolute path)
     * @return the created agent info with scannerId
     */
    public AgentInfo addDynamicAgent(AgentDefinition def, String targetDirectory) {
        String id = UUID.randomUUID().toString();

        // 1. Create scanner for this agent (one-to-one, immediate)
        String scannerId = null;
        if (scannerRegistry != null) {
            try {
                ScannerInfo scannerInfo = scannerRegistry.createForAgent(id, targetDirectory, 5);
                scannerId = scannerInfo.id();
                log.info("Created scanner {} for agent {} (target={}, dir={})",
                        scannerId, id, targetDirectory, targetDirectory);
            } catch (Exception e) {
                log.warn("Failed to create scanner for agent {}, continuing without scanner", id, e);
            }
        }

        // 2. Build the agent's flux (uses scanner's flux if available)
        Flux<PromptResponse> flux = buildFluxForScanner(def, id, targetDirectory);
        Disposable subscription = flux.subscribe();

        // 3. Track in registry
        AgentRegistryEntry entry = new AgentRegistryEntry(id, def, flux,
                LocalDateTime.now(), "DYNAMIC", subscription, scannerId);
        agentRegistry.put(id, entry);

        // 4. Persist agent with scannerId
        if (persistenceService != null) {
            persistenceService.save(id, def, "DYNAMIC", scannerId);
        }

        log.info("Added dynamic agent: {} (scannerId: {})", id, scannerId);

        return new AgentInfo(id, def, entry.createdAt(), true, "DYNAMIC", scannerId);
    }

    /**
     * Build flux for a specific scanner-backed agent.
     */
    private Flux<PromptResponse> buildFluxForScanner(AgentDefinition def, String agentId, String targetDir) {
        if (scannerRegistry != null) {
            Flux<FileHistory> scannerFlux = scannerRegistry.getScannerFlux(agentId);
            return new AgentConfigurator(
                    scannerFlux,
                    chatClient,
                    fileWriter.createPersister(outputDirectory))
                    .configure(def);
        } else {
            return new AgentConfigurator(
                    Flux.empty(),
                    chatClient,
                    fileWriter.createPersister(outputDirectory))
                    .configure(def);
        }
    }

    /**
     * Remove an agent from both memory and database.
     * Also destroys the associated scanner (one-to-one cleanup).
     */
    public void removeAgent(String id) {
        AgentRegistryEntry entry = agentRegistry.remove(id);
        if (entry != null) {
            entry.subscription().dispose();

            // One-to-one: destroy scanner when agent is removed
            if (entry.scannerId() != null && scannerRegistry != null) {
                try {
                    scannerRegistry.destroyForAgent(entry.scannerId());
                    log.info("Destroyed scanner {} for removed agent {}", entry.scannerId(), id);
                } catch (Exception e) {
                    log.warn("Failed to destroy scanner {} for agent {}", entry.scannerId(), id, e);
                }
            }

            log.info("Removed active agent: {}", id);
        } else {
            // Check dormant registry
            DormantAgentEntry dormantEntry = dormantAgents.remove(id);
            if (dormantEntry != null) {
                // Also clean up scanner for dormant agent
                if (dormantEntry.scannerId() != null && scannerRegistry != null) {
                    try {
                        scannerRegistry.destroyForAgent(dormantEntry.scannerId());
                        log.info("Destroyed scanner {} for removed dormant agent {}", dormantEntry.scannerId(), id);
                    } catch (Exception e) {
                        log.warn("Failed to destroy scanner {} for dormant agent {}", dormantEntry.scannerId(), id, e);
                    }
                }
                log.info("Removed dormant agent: {}", id);
            } else {
                log.warn("Agent not found for removal: {}", id);
            }
        }

        // Delete from DB if persistence service is available
        if (persistenceService != null) {
            persistenceService.deleteById(id);
        }
    }

    /**
     * Enable a disabled agent.
     * Re-subscribes the flux and updates the database.
     */
    public AgentInfo enableAgent(String id) {
        // Check if it's in dormant registry
        DormantAgentEntry dormantEntry = dormantAgents.remove(id);
        if (dormantEntry == null) {
            // Check if already active
            if (agentRegistry.containsKey(id)) {
                log.warn("Agent {} is already enabled", id);
                return getAgentInfo(id);
            }
            log.warn("Agent {} not found for enabling", id);
            return null;
        }

        // Update database
        if (persistenceService != null) {
            persistenceService.enable(id);
        }

        // Re-configure and subscribe
        AgentDefinition def = dormantEntry.agentDefinition();
        String targetDir = def.targetDirectory() != null ? def.targetDirectory() : "/tmp";
        Flux<PromptResponse> flux = buildFlux(def, targetDir);
        Disposable subscription = flux.subscribe();

        AgentRegistryEntry entry = new AgentRegistryEntry(id, def, flux, dormantEntry.createdAt(),
                dormantEntry.source(), subscription, dormantEntry.scannerId());
        agentRegistry.put(id, entry);

        log.info("Enabled agent: {} (scannerId: {})", id, dormantEntry.scannerId());
        return new AgentInfo(id, def, entry.createdAt(), true, entry.source(), dormantEntry.scannerId());
    }

    /**
     * Disable an active agent.
     * Disposes the subscription and updates the database.
     */
    public void disableAgent(String id) {
        AgentRegistryEntry entry = agentRegistry.remove(id);
        if (entry == null) {
            log.warn("Agent {} not found for disabling (not active)", id);
            return;
        }

        // Dispose subscription
        entry.subscription().dispose();

        // Update database
        if (persistenceService != null) {
            persistenceService.disable(id);
        }

        // Store in dormant registry
        DormantAgentEntry dormantEntry = new DormantAgentEntry(id, entry.agentDefinition(), entry.createdAt(),
                entry.source(), entry.scannerId());
        dormantAgents.put(id, dormantEntry);

        log.info("Disabled agent: {} (scannerId: {})", id, entry.scannerId());
    }

    /**
     * Update an agent: remove the existing agent (including scanner),
     * then re-add with the updated definition. This ensures clean scanner state.
     *
     * @param id               the agent ID to update
     * @param updatedDefinition the new agent definition
     * @return the updated agent info, or null if the original agent was not found
     */
    public AgentInfo updateAgent(String id, AgentDefinition updatedDefinition) {
        // Remove the existing agent (destroys scanner, removes from registry and DB)
        removeAgent(id);

        // Re-add with updated definition
        String targetDir = updatedDefinition.targetDirectory() != null
                ? updatedDefinition.targetDirectory()
                : "/tmp";
        AgentInfo agentInfo = addDynamicAgent(updatedDefinition, targetDir);

        log.info("Updated agent: {} (scannerId: {})", id, agentInfo.scannerId());
        return agentInfo;
    }

    /**
     * Refresh an agent: dispose current subscription, reset scanner to full-scan mode, re-subscribe.
     * Used when an agent's definition is modified and needs reprocessing.
     *
     * @param agentId the agent to refresh
     * @return the refreshed agent info, or null if not found
     */
    public AgentInfo refreshAgent(String agentId) {
        AgentRegistryEntry entry = agentRegistry.get(agentId);
        if (entry == null) {
            log.warn("Agent {} not found for refresh", agentId);
            return null;
        }

        // Dispose current subscription
        entry.subscription().dispose();

        // Reset scanner to emit all files
        String scannerId = entry.scannerId();
        if (scannerId != null && scannerRegistry != null) {
            try {
                scannerRegistry.refreshAgent(scannerId);
                log.info("Reset scanner {} to full-scan mode for agent {}", scannerId, agentId);
            } catch (Exception e) {
                log.warn("Failed to refresh scanner for agent {}", agentId, e);
            }
        }

        // Re-configure and subscribe with fresh flux
        AgentDefinition def = entry.agentDefinition();
        String targetDir = def.targetDirectory() != null ? def.targetDirectory() : "/tmp";
        Flux<PromptResponse> flux = buildFlux(def, targetDir);
        Disposable subscription = flux.subscribe();

        AgentRegistryEntry newEntry = new AgentRegistryEntry(agentId, def, flux,
                entry.createdAt(), entry.source(), subscription, scannerId);
        agentRegistry.put(agentId, newEntry);

        log.info("Refreshed agent: {} (scannerId: {})", agentId, scannerId);
        return new AgentInfo(agentId, def, newEntry.createdAt(), true, newEntry.source(), scannerId);
    }

    /**
     * List all agents — both active (running) and dormant (disabled).
     */
    public List<AgentInfo> listAgents() {
        List<AgentInfo> result = new ArrayList<>();

        // Active agents
        for (AgentRegistryEntry entry : agentRegistry.values()) {
            result.add(new AgentInfo(entry.id(), entry.agentDefinition(), entry.createdAt(), true,
                    entry.source(), entry.scannerId()));
        }

        // Dormant agents
        for (DormantAgentEntry entry : dormantAgents.values()) {
            result.add(new AgentInfo(entry.id(), entry.agentDefinition(), entry.createdAt(), false,
                    entry.source(), entry.scannerId()));
        }

        return result;
    }

    /**
     * Get an agent info by ID (searches both active and dormant).
     */
    public AgentInfo getAgentInfo(String id) {
        AgentRegistryEntry activeEntry = agentRegistry.get(id);
        if (activeEntry != null) {
            return new AgentInfo(activeEntry.id(), activeEntry.agentDefinition(), activeEntry.createdAt(), true,
                    activeEntry.source(), activeEntry.scannerId());
        }

        DormantAgentEntry dormantEntry = dormantAgents.get(id);
        if (dormantEntry != null) {
            return new AgentInfo(dormantEntry.id(), dormantEntry.agentDefinition(), dormantEntry.createdAt(), false,
                    dormantEntry.source(), dormantEntry.scannerId());
        }

        return null;
    }

    /**
     * Get count of active (running) agents.
     */
    public int getActiveAgentCount() {
        return agentRegistry.size();
    }

    /**
     * Get count of dormant (disabled) agents.
     */
    public int getDormantAgentCount() {
        return dormantAgents.size();
    }

    /**
     * Check if the manager has scanner registry support.
     */
    public boolean hasScannerRegistry() {
        return scannerRegistry != null;
    }

    private record AgentRegistryEntry(String id, AgentDefinition agentDefinition, Flux<PromptResponse> flux,
            LocalDateTime createdAt, String source, Disposable subscription, String scannerId) {
    }

    private record DormantAgentEntry(String id, AgentDefinition agentDefinition, LocalDateTime createdAt,
            String source, String scannerId) {
    }
}

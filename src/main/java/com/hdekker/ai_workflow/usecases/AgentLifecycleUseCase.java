package com.hdekker.ai_workflow.usecases;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hdekker.ai_workflow.adapter.inbound.rest.dto.AgentInfo;
import com.hdekker.ai_workflow.adapter.outbound.file.FileWriter;
import com.hdekker.ai_workflow.adapter.outbound.file.TargetDirectoryValidator;
import com.hdekker.ai_workflow.adapter.outbound.file.TargetDirectoryValidator.ValidationResult;
import com.hdekker.ai_workflow.adapter.outbound.persistence.agent.AgentEntity;
import com.hdekker.ai_workflow.adapter.outbound.persistence.agent.AgentRepositoryAdapter;
import com.hdekker.ai_workflow.app.pipeline.AgentConfigurator;
import com.hdekker.ai_workflow.app.pipeline.management.ScannerRegistry;
import com.hdekker.ai_workflow.domain.agent.AgentDefinition;
import com.hdekker.ai_workflow.domain.file.FileHistory;
import com.hdekker.ai_workflow.domain.prompt.PromptResponse;

import org.springframework.ai.chat.client.ChatClient;
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
    private final AgentRepositoryAdapter persistenceService;
    private final TargetDirectoryValidator targetDirectoryValidator;

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
        this.targetDirectoryValidator = null;
    }

    /**
     * Full constructor with Spring-managed dependencies.
     *
     * @param scannerRegistry          registry for per-agent scanner instances
     * @param fileWriter               file writer abstraction
     * @param outputDirectory          default output directory
     * @param chatClient               LLM chat client
     * @param persistenceService       agent persistence usecase (may be null)
     * @param targetDirectoryValidator target directory validator
     */
    public AgentLifecycleUseCase(ScannerRegistry scannerRegistry,
                                 FileWriter fileWriter,
                                 Path outputDirectory,
                                 ChatClient chatClient,
                                 AgentRepositoryAdapter persistenceService,
                                 TargetDirectoryValidator targetDirectoryValidator) {
        this.scannerRegistry = scannerRegistry;
        this.fileWriter = fileWriter;
        this.outputDirectory = outputDirectory;
        this.chatClient = chatClient;
        this.persistenceService = persistenceService;
        this.targetDirectoryValidator = targetDirectoryValidator;
    }

    /**
     * Initialize agents from YAML configuration.
     * YAML agents share a default scanner (if scannerRegistry is available).
     * Persists each agent to the database with active=true.
     */
    public void initializeFromYAML(List<AgentDefinition> yamlAgents) {
        yamlAgents.forEach(agent -> {
            String id = agent.title(); // Use title as ID for YAML agents
            ValidationResult result = validateTargetDirectory(agent.targetDirectory());
            if (!result.valid()) {
                log.warn("Agent {} has no valid targetDirectory: {}. Initialization halted.", id, result.reason());
                // Persist to DB but skip scanner/flux/subscription
                if (persistenceService != null) {
                    persistenceService.save(id, agent, "YAML");
                }
                return;
            }

            String targetDir = agent.targetDirectory();
            Flux<PromptResponse> flux = buildFlux(agent, targetDir);
            Disposable subscription = flux.subscribe();

            if (scannerRegistry != null) {
                scannerRegistry.createForAgent(id, targetDir, 5);
            }

            AgentRegistryEntry entry = new AgentRegistryEntry(id, agent, flux, LocalDateTime.now(), "YAML",
                    subscription);
            agentRegistry.put(id, entry);

            // Persist to DB if persistence service is available
            if (persistenceService != null) {
                persistenceService.save(id, agent, "YAML");
            }

            log.info("Initialized YAML agent: {}", id);
        });
    }

    /**
     * Restore agents from the database on startup.
     * Active agents get flux/subscription AND a new scanner created in the ScannerRegistry.
     * Disabled agents stay dormant (no scanner created).
     */
    public void restoreFromDatabase() {
        if (persistenceService == null) {
            return;
        }

        // Restore active (enabled) agents — creates scanners for each
        List<AgentEntity> activeEntities = persistenceService.findAllActive();
        int restoredCount = 0;
        int skippedCount = 0;
        for (AgentEntity entity : activeEntities) {
            try {
                Optional<AgentDefinition> definitionOpt = persistenceService.getDefinition(entity.getId());
                if (definitionOpt.isPresent()) {
                    AgentDefinition def = definitionOpt.get();
                    ValidationResult result = validateTargetDirectory(def.targetDirectory());
                    if (!result.valid()) {
                        log.warn("Agent {} has no valid targetDirectory: {}. Initialization halted.",
                                entity.getId(), result.reason());
                        skippedCount++;
                        continue;
                    }

                    String targetDir = def.targetDirectory();
                    // Create a new scanner for this restored agent (one-to-one)
                    if (scannerRegistry != null) {
                        scannerRegistry.createForAgent(entity.getId(), targetDir, 5);
                    }

                    Flux<PromptResponse> flux = buildFlux(def, targetDir);
                    Disposable subscription = flux.subscribe();

                    AgentRegistryEntry entry = new AgentRegistryEntry(entity.getId(), def, flux, entity.getCreatedAt(),
                            entity.getSource(), subscription);
                    agentRegistry.put(entity.getId(), entry);
                    restoredCount++;
                    log.info("Restored active agent from DB: {} (source: {})",
                            entity.getId(), entity.getSource());
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
                            entity.getCreatedAt(), entity.getSource());
                    dormantAgents.put(entity.getId(), dormantEntry);
                    dormantCount++;
                    log.info("Loaded dormant agent from DB: {} (source: {})",
                            entity.getId(), entity.getSource());
                }
            } catch (Exception e) {
                log.error("Failed to load dormant agent from DB: {}", entity.getId(), e);
            }
        }

        log.info("Restored {} active agents and {} dormant agents from database (skipped {} with invalid targetDirectory)",
                restoredCount, dormantCount, skippedCount);
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
     * @return the created agent info
     */
    public AgentInfo addDynamicAgent(AgentDefinition def, String targetDirectory) {
        String id = UUID.randomUUID().toString();

        // 1. Create scanner for this agent (one-to-one, immediate)
        if (scannerRegistry != null) {
            try {
                scannerRegistry.createForAgent(id, targetDirectory, 5);
                log.info("Created scanner for agent {} (target={}, dir={})",
                        id, targetDirectory, targetDirectory);
            } catch (Exception e) {
                log.warn("Failed to create scanner for agent {}, continuing without scanner", id, e);
            }
        }

        // 2. Build the agent's flux (uses scanner's flux if available)
        Flux<PromptResponse> flux = buildFluxForScanner(def, id, targetDirectory);
        Disposable subscription = flux.subscribe();

        // 3. Track in registry
        AgentRegistryEntry entry = new AgentRegistryEntry(id, def, flux,
                LocalDateTime.now(), "DYNAMIC", subscription);
        agentRegistry.put(id, entry);

        // 4. Persist agent
        if (persistenceService != null) {
            persistenceService.save(id, def, "DYNAMIC");
        }

        log.info("Added dynamic agent: {}", id);

        return new AgentInfo(id, def, entry.createdAt(), true, "DYNAMIC");
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
            if (scannerRegistry != null) {
                try {
                    scannerRegistry.destroyForAgent(id);
                    log.info("Destroyed scanner for removed agent {}", id);
                } catch (Exception e) {
                    log.warn("Failed to destroy scanner for agent {}", id, e);
                }
            }

            log.info("Removed active agent: {}", id);
        } else {
            // Check dormant registry
            DormantAgentEntry dormantEntry = dormantAgents.remove(id);
            if (dormantEntry != null) {
                // Also clean up scanner for dormant agent
                if (scannerRegistry != null) {
                    try {
                        scannerRegistry.destroyForAgent(id);
                        log.info("Destroyed scanner for removed dormant agent {}", id);
                    } catch (Exception e) {
                        log.warn("Failed to destroy scanner for dormant agent {}", id, e);
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
        ValidationResult result = validateTargetDirectory(def.targetDirectory());
        if (!result.valid()) {
            log.warn("Agent {} has no valid targetDirectory: {}. Re-initialization halted.", id, result.reason());
            // Put the agent back into dormant registry
            dormantAgents.put(id, dormantEntry);
            return null;
        }

        String targetDir = def.targetDirectory();
        Flux<PromptResponse> flux = buildFlux(def, targetDir);
        Disposable subscription = flux.subscribe();

        AgentRegistryEntry entry = new AgentRegistryEntry(id, def, flux, dormantEntry.createdAt(),
                dormantEntry.source(), subscription);
        agentRegistry.put(id, entry);

        log.info("Enabled agent: {}", id);
        return new AgentInfo(id, def, entry.createdAt(), true, entry.source());
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
                entry.source());
        dormantAgents.put(id, dormantEntry);

        log.info("Disabled agent: {}", id);
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
        ValidationResult result = validateTargetDirectory(updatedDefinition.targetDirectory());
        if (!result.valid()) {
            log.warn("Agent {} has no valid targetDirectory: {}. Update halted.", id, result.reason());
            return null;
        }

        String targetDir = updatedDefinition.targetDirectory();
        AgentInfo agentInfo = addDynamicAgent(updatedDefinition, targetDir);

        log.info("Updated agent: {}", id);
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
        if (scannerRegistry != null) {
            try {
                scannerRegistry.refreshAgent(agentId);
                log.info("Reset scanner to full-scan mode for agent {}", agentId);
            } catch (Exception e) {
                log.warn("Failed to refresh scanner for agent {}", agentId, e);
            }
        }

        // Re-configure and subscribe with fresh flux
        AgentDefinition def = entry.agentDefinition();
        ValidationResult validationResult = validateTargetDirectory(def.targetDirectory());
        if (!validationResult.valid()) {
            log.warn("Agent {} has no valid targetDirectory: {}. Refresh halted.", agentId, validationResult.reason());
            return null;
        }

        String targetDir = def.targetDirectory();
        Flux<PromptResponse> flux = buildFlux(def, targetDir);
        Disposable subscription = flux.subscribe();

        AgentRegistryEntry newEntry = new AgentRegistryEntry(agentId, def, flux,
                entry.createdAt(), entry.source(), subscription);
        agentRegistry.put(agentId, newEntry);

        log.info("Refreshed agent: {}", agentId);
        return new AgentInfo(agentId, def, newEntry.createdAt(), true, newEntry.source());
    }

    /**
     * List all agents — both active (running) and dormant (disabled).
     */
    public List<AgentInfo> listAgents() {
        List<AgentInfo> result = new ArrayList<>();

        // Active agents
        for (AgentRegistryEntry entry : agentRegistry.values()) {
            result.add(new AgentInfo(entry.id(), entry.agentDefinition(), entry.createdAt(), true,
                    entry.source()));
        }

        // Dormant agents
        for (DormantAgentEntry entry : dormantAgents.values()) {
            result.add(new AgentInfo(entry.id(), entry.agentDefinition(), entry.createdAt(), false,
                    entry.source()));
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
                    activeEntry.source());
        }

        DormantAgentEntry dormantEntry = dormantAgents.get(id);
        if (dormantEntry != null) {
            return new AgentInfo(dormantEntry.id(), dormantEntry.agentDefinition(), dormantEntry.createdAt(), false,
                    dormantEntry.source());
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

    /**
     * Validates the target directory using the configured validator (if present).
     * Returns {@code ValidationResult.valid()} when no validator is configured (e.g. unit tests
     * using the no-arg constructor).
     *
     * @param targetDir the target directory path to validate (may be null)
     * @return the validation result
     */
    private ValidationResult validateTargetDirectory(String targetDir) {
        if (targetDirectoryValidator == null) {
            // No validator configured — assume valid (e.g. unit tests without Spring)
            return ValidationResult.success();
        }
        return targetDirectoryValidator.validate(targetDir);
    }

    private record AgentRegistryEntry(String id, AgentDefinition agentDefinition, Flux<PromptResponse> flux,
            LocalDateTime createdAt, String source, Disposable subscription) {
    }

    private record DormantAgentEntry(String id, AgentDefinition agentDefinition, LocalDateTime createdAt,
            String source) {
    }
}

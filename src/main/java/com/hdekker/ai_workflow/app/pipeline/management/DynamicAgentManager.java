package com.hdekker.ai_workflow.app.pipeline.management;

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
import com.hdekker.ai_workflow.database.agent.AgentEntity;
import com.hdekker.ai_workflow.database.agent.AgentPersistenceService;
import com.hdekker.ai_workflow.files.FileScanner;
import com.hdekker.ai_workflow.files.FileWriter;
import com.hdekker.ai_workflow.pipeline.domain.AgentDefinition;
import com.hdekker.ai_workflow.prompt.PromptResponse;
import com.hdekker.ai_workflow.rest.dto.AgentInfo;

import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import java.nio.file.Path;

public class DynamicAgentManager {

	private static final Logger log = LoggerFactory.getLogger(DynamicAgentManager.class);

	private final Map<String, AgentRegistryEntry> agentRegistry = new ConcurrentHashMap<>();
	private final Map<String, DormantAgentEntry> dormantAgents = new ConcurrentHashMap<>();

	private final AgentConfigurator agentConfigurator;
	private final AgentPersistenceService persistenceService;

	// Constructor with abstractions
	public DynamicAgentManager(FileScanner fileScanner, FileWriter fileWriter, Path outputDirectory,
			ChatClient chatClient) {
		this.agentConfigurator = new AgentConfigurator(fileScanner.flux(), chatClient,
				fileWriter.createPersister(outputDirectory));
		this.persistenceService = null; // Not available when constructed outside Spring
	}

	// Constructor with Spring-managed persistence service
	public DynamicAgentManager(FileScanner fileScanner, FileWriter fileWriter, Path outputDirectory,
			ChatClient chatClient, AgentPersistenceService persistenceService) {
		this.agentConfigurator = new AgentConfigurator(fileScanner.flux(), chatClient,
				fileWriter.createPersister(outputDirectory));
		this.persistenceService = persistenceService;
	}

	/**
	 * Initialize agents from YAML configuration.
	 * Persists each agent to the database with active=true.
	 */
	public void initializeFromYAML(List<AgentDefinition> yamlAgents) {
		yamlAgents.forEach(agent -> {
			String id = agent.title(); // Use title as ID for YAML agents
			Flux<PromptResponse> flux = agentConfigurator.configure(agent);
			Disposable subscription = flux.subscribe();

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
	 * Active agents get flux/subscription; disabled agents stay dormant.
	 */
	public void restoreFromDatabase() {
		if (persistenceService == null) {
			return;
		}

		// Restore active (enabled) agents
		List<AgentEntity> activeEntities = persistenceService.findAllActive();
		int restoredCount = 0;
		for (AgentEntity entity : activeEntities) {
			try {
				Optional<AgentDefinition> definitionOpt = persistenceService.getDefinition(entity.getId());
				if (definitionOpt.isPresent()) {
					AgentDefinition def = definitionOpt.get();
					Flux<PromptResponse> flux = agentConfigurator.configure(def);
					Disposable subscription = flux.subscribe();

					AgentRegistryEntry entry = new AgentRegistryEntry(entity.getId(), def, flux, entity.getCreatedAt(),
							entity.getSource(), subscription);
					agentRegistry.put(entity.getId(), entry);
					restoredCount++;
					log.info("Restored active agent from DB: {} (source: {})", entity.getId(), entity.getSource());
				}
			} catch (Exception e) {
				log.error("Failed to restore agent from DB: {}", entity.getId(), e);
			}
		}

		// Load disabled agents as dormant
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
					log.info("Loaded dormant agent from DB: {} (source: {})", entity.getId(), entity.getSource());
				}
			} catch (Exception e) {
				log.error("Failed to load dormant agent from DB: {}", entity.getId(), e);
			}
		}

		log.info("Restored {} active agents and {} dormant agents from database", restoredCount, dormantCount);
	}

	/**
	 * Add a new dynamic agent.
	 * Persists to DB with active=true.
	 */
	public AgentInfo addDynamicAgent(AgentDefinition def) {
		String id = UUID.randomUUID().toString();
		Flux<PromptResponse> flux = agentConfigurator.configure(def);
		Disposable subscription = flux.subscribe();

		AgentRegistryEntry entry = new AgentRegistryEntry(id, def, flux, LocalDateTime.now(), "DYNAMIC", subscription);
		agentRegistry.put(id, entry);

		// Persist to DB if persistence service is available
		if (persistenceService != null) {
			persistenceService.save(id, def, "DYNAMIC");
		}

		log.info("Added dynamic agent: {}", id);

		return new AgentInfo(id, def, entry.createdAt(), true, "DYNAMIC");
	}

	/**
	 * Remove an agent from both memory and database.
	 */
	public void removeAgent(String id) {
		AgentRegistryEntry entry = agentRegistry.remove(id);
		if (entry != null) {
			entry.subscription().dispose();
			log.info("Removed active agent: {}", id);
		} else {
			// Check dormant registry
			DormantAgentEntry dormantEntry = dormantAgents.remove(id);
			if (dormantEntry != null) {
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
		Flux<PromptResponse> flux = agentConfigurator.configure(def);
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
	 * List all agents — both active (running) and dormant (disabled).
	 */
	public List<AgentInfo> listAgents() {
		List<AgentInfo> result = new ArrayList<>();

		// Active agents
		for (AgentRegistryEntry entry : agentRegistry.values()) {
			result.add(new AgentInfo(entry.id(), entry.agentDefinition(), entry.createdAt(), true, entry.source()));
		}

		// Dormant agents
		for (DormantAgentEntry entry : dormantAgents.values()) {
			result.add(new AgentInfo(entry.id(), entry.agentDefinition(), entry.createdAt(), false, entry.source()));
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

	private record AgentRegistryEntry(String id, AgentDefinition agentDefinition, Flux<PromptResponse> flux,
			LocalDateTime createdAt, String source, Disposable subscription) {
	}

	private record DormantAgentEntry(String id, AgentDefinition agentDefinition, LocalDateTime createdAt, String source) {
	}
}

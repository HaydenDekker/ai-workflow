package com.hdekker.ai_workflow.database.agent;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.hdekker.ai_workflow.domain.agent.AgentDefinition;

/**
 * Use case layer to handle entity↔domain mapping for agents.
 * Handles JSON serialization of AgentDefinition and CRUD operations.
 */
@Service
public class AgentPersistenceUsecase {

	private static final Logger log = LoggerFactory.getLogger(AgentPersistenceUsecase.class);

	private final AgentRepository agentRepository;
	private final ObjectMapper objectMapper;

	public AgentPersistenceUsecase(AgentRepository agentRepository, ObjectMapper objectMapper) {
		this.agentRepository = agentRepository;
		this.objectMapper = objectMapper;
	}

	/**
	 * Save an agent (create or update).
	 * New agents are created with active=true by default.
	 * Existing agents are updated with new definition but preserve createdAt.
	 *
	 * @param id          the agent ID
	 * @param definition  the agent definition
	 * @param source      the source ("YAML" or "DYNAMIC")
	 */
	public AgentEntity save(String id, AgentDefinition definition, String source) {
		AgentEntity entity = agentRepository.findById(id).orElseGet(AgentEntity::new);
		entity.setId(id);
		try {
			entity.setAgentDefinitionJson(objectMapper.writeValueAsString(definition));
		} catch (JsonProcessingException e) {
			throw new RuntimeException("Failed to serialize AgentDefinition for agent: " + id, e);
		}
		entity.setTitle(definition.title());
		entity.setSource(source);
		if (entity.getCreatedAt() == null) {
			entity.setCreatedAt(LocalDateTime.now());
		}
		entity.setActive(true);
		return agentRepository.save(entity);
	}

	/**
	 * Load agent definition from DB by id.
	 */
	public Optional<AgentDefinition> getDefinition(String id) {
		return agentRepository.findById(id)
				.map(entity -> {
					try {
						return objectMapper.readValue(entity.getAgentDefinitionJson(), AgentDefinition.class);
					} catch (JsonProcessingException e) {
						throw new RuntimeException("Failed to deserialize AgentDefinition for agent: " + id, e);
					}
				});
	}

	/**
	 * List all agents ordered by creation date descending.
	 */
	public List<AgentEntity> listAll() {
		return agentRepository.findAllByOrderByCreatedAtDesc();
	}

	/**
	 * Delete an agent by id.
	 */
	public void deleteById(String id) {
		agentRepository.deleteById(id);
	}

	/**
	 * Toggle agent on/off.
	 * When enabling, sets lastStartedAt to now.
	 */
	public void toggle(String id, boolean enable) {
		agentRepository.findById(id).ifPresent(entity -> {
			entity.setActive(enable);
			if (enable) {
				entity.setLastStartedAt(LocalDateTime.now());
			}
			agentRepository.save(entity);
			log.info("Agent {} toggled to {}", id, enable ? "enabled" : "disabled");
		});
	}

	/**
	 * Enable an agent.
	 */
	public void enable(String id) {
		toggle(id, true);
	}

	/**
	 * Disable an agent.
	 */
	public void disable(String id) {
		toggle(id, false);
	}

	/**
	 * Restore all enabled agents from DB (for startup).
	 */
	public List<AgentEntity> findAllActive() {
		return agentRepository.findByActiveTrueOrderByCreatedAtDesc();
	}

	/**
	 * Get all agents (for UI listing — shows enabled and disabled).
	 */
	public List<AgentEntity> findAllOrdered() {
		return agentRepository.findAllByOrderByCreatedAtDesc();
	}

	/**
	 * Count active (enabled) agents.
	 */
	public long countActive() {
		return agentRepository.countByActiveTrue();
	}

	/**
	 * Count inactive (disabled) agents.
	 */
	public long countInactive() {
		return agentRepository.countByActiveFalse();
	}
}

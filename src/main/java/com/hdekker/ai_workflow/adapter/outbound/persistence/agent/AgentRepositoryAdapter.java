package com.hdekker.ai_workflow.adapter.outbound.persistence.agent;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hdekker.ai_workflow.application.agent.port.AgentRepository;
import com.hdekker.ai_workflow.domain.agent.AgentDefinition;
import com.hdekker.ai_workflow.domain.agent.AgentSource;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

/**
 * Outbound adapter implementing {@link AgentRepository} port.
 * <p>
 * Handles entity↔domain mapping for agents, including JSON serialization
 * of {@link AgentDefinition} and CRUD operations against the JPA repository.
 */
@Service
public class AgentRepositoryAdapter implements AgentRepository {

	private static final Logger log = LoggerFactory.getLogger(AgentRepositoryAdapter.class);

	private final AgentJpaRepository agentRepository;
	private final ObjectMapper objectMapper;

	public AgentRepositoryAdapter(AgentJpaRepository agentRepository, ObjectMapper objectMapper) {
		this.agentRepository = agentRepository;
		this.objectMapper = objectMapper;
	}

	// ── AgentRepository port implementation ─────────────────────────

	@Override
	public void save(String id, AgentDefinition definition, AgentSource source) {
		AgentEntity entity = agentRepository.findById(id).orElseGet(AgentEntity::new);
		entity.setId(id);
		entity.setAgentDefinitionJson(definition);
		entity.setTitle(definition.title());
		entity.setSource(source);
		if (entity.getCreatedAt() == null) {
			entity.setCreatedAt(LocalDateTime.now());
		}
		entity.setActive(true);
		agentRepository.save(entity);
	}

	@Override
	public Optional<AgentDefinition> findById(String id) {
		return agentRepository.findById(id)
				.flatMap(this::deserializeDefinition);
	}

	@Override
	public boolean existsById(String id) {
		return agentRepository.existsById(id);
	}

	@Override
	public List<AgentDefinition> findAllActive() {
		return agentRepository.findByActiveTrueOrderByCreatedAtDesc().stream()
				.map(this::deserializeDefinition)
				.filter(Optional::isPresent)
				.map(Optional::get)
				.toList();
	}

	@Override
	public List<AgentDefinition> findAllOrdered() {
		return agentRepository.findAllByOrderByCreatedAtDesc().stream()
				.map(this::deserializeDefinition)
				.filter(Optional::isPresent)
				.map(Optional::get)
				.toList();
	}

	@Override
	public void deleteById(String id) {
		agentRepository.deleteById(id);
	}

	@Override
	public void enable(String id) {
		toggle(id, true);
	}

	@Override
	public void disable(String id) {
		toggle(id, false);
	}

	@Override
	public long countActive() {
		return agentRepository.countByActiveTrue();
	}

	@Override
	public long countInactive() {
		return agentRepository.countByActiveFalse();
	}

	// ── Backward-compatible methods (tests + legacy AgentLifecycleUseCase) ─

	/**
	 * Save an agent and return the persisted entity.
	 * <p>
	 * Backward-compatible method used by tests and the legacy {@code AgentLifecycleUseCase}.
	 *
	 * @param id          the agent ID
	 * @param definition  the agent definition
	 * @param source      the source (YAML or DYNAMIC)
	 * @return the persisted entity
	 */
	public AgentEntity saveAndGetEntity(String id, AgentDefinition definition, AgentSource source) {
		AgentEntity entity = agentRepository.findById(id).orElseGet(AgentEntity::new);
		entity.setId(id);
		entity.setAgentDefinitionJson(definition);
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
	 * <p>
	 * Backward-compatible alias for {@link #findById(String)}.
	 */
	public Optional<AgentDefinition> getDefinition(String id) {
		return findById(id);
	}

	/**
	 * List all agents ordered by creation date descending (returns entities).
	 * <p>
	 * Backward-compatible method returning entities, used by tests.
	 */
	public List<AgentEntity> listAll() {
		return agentRepository.findAllByOrderByCreatedAtDesc();
	}

	/**
	 * Restore all enabled agents from DB (for startup) — returns entities.
	 * <p>
	 * Backward-compatible method returning entities, used by tests and
	 * the legacy {@code AgentLifecycleUseCase}.
	 */
	public List<AgentEntity> findAllActiveEntities() {
		return agentRepository.findByActiveTrueOrderByCreatedAtDesc();
	}

	/**
	 * Get all agents ordered (for UI listing — shows enabled and disabled) — returns entities.
	 * <p>
	 * Backward-compatible method returning entities, used by tests and
	 * the legacy {@code AgentLifecycleUseCase}.
	 */
	public List<AgentEntity> findAllOrderedEntities() {
		return agentRepository.findAllByOrderByCreatedAtDesc();
	}

	// ── Internal helpers ─────────────────────────────────────────────

	private void toggle(String id, boolean enable) {
		agentRepository.findById(id).ifPresent(entity -> {
			entity.setActive(enable);
			if (enable) {
				entity.setLastStartedAt(LocalDateTime.now());
			}
			agentRepository.save(entity);
			log.info("Agent {} toggled to {}", id, enable ? "enabled" : "disabled");
		});
	}

	private Optional<AgentDefinition> deserializeDefinition(AgentEntity entity) {
		return Optional.ofNullable(entity.getAgentDefinitionJson());
	}
}

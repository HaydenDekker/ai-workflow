package com.hdekker.ai_workflow.application.agent.port;

import com.hdekker.ai_workflow.domain.agent.AgentDefinition;

import java.util.List;
import java.util.Optional;

/**
 * Port interface for agent persistence.
 * <p>
 * Declares the operations the application layer needs to persist and retrieve
 * agent definitions. Infrastructure adapters (JPA, etc.) implement this port.
 */
public interface AgentRepository {

    /**
     * Save an agent definition. Creates or updates the existing entry.
     *
     * @param id         the unique agent identifier
     * @param definition the agent definition to persist
     * @param source     the source type ("YAML" or "DYNAMIC")
     */
    void save(String id, AgentDefinition definition, String source);

    /**
     * Load an agent definition by ID.
     *
     * @param id the agent identifier
     * @return the definition if found
     */
    Optional<AgentDefinition> findById(String id);

    /**
     * Check whether an agent exists in the store.
     *
     * @param id the agent identifier
     * @return true if the agent exists
     */
    boolean existsById(String id);

    /**
     * List all active (enabled) agents, ordered by creation date descending.
     *
     * @return the list of active agent definitions
     */
    List<AgentDefinition> findAllActive();

    /**
     * List all agents ordered by creation date descending (includes disabled/dormant).
     *
     * @return the list of all agent definitions
     */
    List<AgentDefinition> findAllOrdered();

    /**
     * Delete an agent by ID.
     *
     * @param id the agent identifier
     */
    void deleteById(String id);

    /**
     * Enable an agent.
     *
     * @param id the agent identifier
     */
    void enable(String id);

    /**
     * Disable an agent.
     *
     * @param id the agent identifier
     */
    void disable(String id);

    /**
     * Count active (enabled) agents.
     *
     * @return the number of active agents
     */
    long countActive();

    /**
     * Count inactive (disabled) agents.
     *
     * @return the number of inactive agents
     */
    long countInactive();
}

package com.hdekker.ai_workflow.adapter.outbound.persistence.agent;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for agent persistence.
 * Auto-creates table: agent
 */
@Repository
public interface AgentJpaRepository extends JpaRepository<AgentEntity, String> {

	List<AgentEntity> findAllByOrderByCreatedAtDesc();

	List<AgentEntity> findByActiveTrueOrderByCreatedAtDesc();

	List<AgentEntity> findByActiveFalseOrderByCreatedAtDesc();

	long countByActiveTrue();

	long countByActiveFalse();
}

package com.hdekker.ai_workflow.adapter.outbound.persistence.llmstatus;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for LLM status persistence.
 * Auto-creates table: llm_status
 */
@Repository
public interface LLMStatusJpaRepository extends JpaRepository<LLMStatusEntity, String> {
	
	Optional<LLMStatusEntity> findByEndpoint(String endpoint);
	
	List<LLMStatusEntity> findAll();
	
}

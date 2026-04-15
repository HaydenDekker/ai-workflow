package com.hdekker.ai_workflow.database.llmstatus;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for LLM status persistence.
 * Auto-creates table: llm_status
 */
@Repository
public interface LLMStatusRepository extends JpaRepository<LLMStatusEntity, String> {
	
	Optional<LLMStatusEntity> findByEndpoint(String endpoint);
	
	List<LLMStatusEntity> findAll();
	
}

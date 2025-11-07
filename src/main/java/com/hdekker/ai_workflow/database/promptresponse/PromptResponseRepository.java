package com.hdekker.ai_workflow.database.promptresponse;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PromptResponseRepository extends JpaRepository<PromptResponseEntity, Integer>{
	List<PromptResponseEntity> findAllByPromptTitle(String promptTitle);
}

package com.hdekker.ai_workflow.database.promptresponse;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hdekker.ai_workflow.pipeline.PromptResponse;

public interface PromptResponseRepository extends JpaRepository<PromptResponseEntity, Integer>{
	List<PromptResponseEntity> findAllByPromptTitle(String promptTitle);
}

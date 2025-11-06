package com.hdekker.ai_workflow.llm;

import java.util.List;

import com.hdekker.ai_workflow.pipeline.PromptResponse;

/**
 * Highly likely a prompt could return 
 * with multiple suggestions or event no suggestions.
 * 
 */
public interface PromptResponseConverter {
	List<PromptResponse> convert(PromptResponse promptResponse);
}

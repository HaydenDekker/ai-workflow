package com.hdekker.ai_workflow.pipeline;

import java.util.List;

import com.hdekker.ai_workflow.prompt.PromptResponse;

/**
 * Highly likely a prompt could return 
 * with multiple suggestions or event no suggestions.
 * 
 */
public interface SplittableStrategy<R, K> {
	List<K> split(R agregateItem);
	
	public static <K> SplittableStrategy<K,K> noSPLT(){
		return (r) -> List.of(r);
	}
	
}

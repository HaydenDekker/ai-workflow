package com.hdekker.ai_workflow.pipeline;

import java.util.List;

/**
 * Highly likely a prompt could return 
 * with multiple suggestions or event no suggestions.
 * 
 */
public interface SplittableStrategy<R, K> {
	List<K> split(R agregateItem);
}

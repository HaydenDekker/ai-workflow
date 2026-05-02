package com.hdekker.ai_workflow.application.pipeline;

import java.util.List;

import com.hdekker.ai_workflow.domain.prompt.PromptResponse;

/**
 * Strategy for splitting aggregated prompt responses into individual items.
 * <p>
 * Application-layer pipeline strategy — works with domain models only.
 */
public interface SplittableStrategy {
	/**
	 * Splits an aggregated prompt response into a list of individual responses.
	 *
	 * @param aggregateItem the aggregated response to split
	 * @return a list of individual prompt responses
	 */
	List<PromptResponse> split(PromptResponse aggregateItem);

	/**
	 * Returns a no-op strategy that wraps the response in a single-item list.
	 */
	static SplittableStrategy noSPLT() {
		return (r) -> List.of(r);
	}
}

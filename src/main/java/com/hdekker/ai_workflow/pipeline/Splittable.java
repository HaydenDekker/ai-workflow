package com.hdekker.ai_workflow.pipeline;

public interface Splittable<T,R,K> {
	
	PromptPipelineBuilder<T,K> split(SplittableStrategy<R, K> splitter);

}

package com.hdekker.ai_workflow.pipeline;

import com.hdekker.ai_workflow.llm.PromptResponseConverter;

public interface Splittable<T,K> {
	
	PromptPipelineBuilder<T,K> split(PromptResponseConverter<K> splitter);

}

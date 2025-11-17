package com.hdekker.ai_workflow.pipeline.domain;

import java.util.List;

public record PromptChain(
		List<PipelinePrompt> chain
		) {

}

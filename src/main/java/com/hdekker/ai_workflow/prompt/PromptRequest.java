package com.hdekker.ai_workflow.prompt;

import com.hdekker.ai_workflow.pipeline.domain.PipelinePrompt;

public record PromptRequest(
		PipelinePrompt pipelinePrompt, // TODO rename to prompt template.
		String file, // TODO further supports Message header use.
		String fileURL
		) {}

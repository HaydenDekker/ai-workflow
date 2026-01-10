package com.hdekker.ai_workflow.prompt;


public record PromptRequest(
		String file, // TODO further supports Message header use.
		String fileURL
		) {}

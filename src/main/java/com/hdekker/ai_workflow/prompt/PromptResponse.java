package com.hdekker.ai_workflow.prompt;

import com.hdekker.ai_workflow.pipeline.domain.PipelinePrompt;

public record PromptResponse(PipelinePrompt prompt, String fileName, String file, String response) {

}

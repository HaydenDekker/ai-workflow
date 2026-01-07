package com.hdekker.ai_workflow.prompt;

import com.hdekker.ai_workflow.pipeline.domain.PipelinePrompt;

public record PromptResponse(PipelinePrompt prompt, String fileName, String fileContents, String response) {

	public String createOutputFileName() {
		return prompt().title() + "_" + fileName() + ".md";
	}

}

package com.hdekker.ai_workflow;

import com.hdekker.ai_workflow.pipeline.domain.PipelinePrompt;
import com.hdekker.ai_workflow.prompt.PromptResponse;

public class TestData {
	
	public static PipelinePrompt basicPrompt() {
		return new PipelinePrompt(
				"/**",  
				"BASIC PROMPT TEST", 
				"STANDARD",
				"This prompt is part of a basic pipeline stage configuration. You should simply confirm you've received this prompt.", 
				"Neat and tidy output is required.");
	}
	
	public static PromptResponse basicResponse() {
		return new PromptResponse(
				basicPrompt(), 
				"input-file.txt", 
				"This is the content of the input file.", 
				"This is the content after the pipeline stage has parsed the input file via an llm and appended any additional information.");
		
	}

}

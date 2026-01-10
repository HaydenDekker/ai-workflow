package com.hdekker.ai_workflow;

import com.hdekker.ai_workflow.pipeline.domain.PipelinePrompt;
import com.hdekker.ai_workflow.prompt.PromptRequest;
import com.hdekker.ai_workflow.prompt.PromptResponse;

public class TestData {
	
	static String fileNameStub = "input-file.txt";
	static String fileContentStub = "This is the content of the input file.";
	
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
				basicRequest().fileURL(),
				basicRequest().file(), 
				"This is the content after the pipeline stage has parsed the input file via an llm and appended any additional information.");
		
	}

	public static PromptRequest basicRequest() {
		return new PromptRequest(fileNameStub, fileContentStub);
	}

}

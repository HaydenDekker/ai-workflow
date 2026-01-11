package com.hdekker.ai_workflow.prompt;

import com.hdekker.ai_workflow.app.pipeline.OutputFilenameTemplate;
import com.hdekker.ai_workflow.app.pipeline.RegexInputFileFilter;
import com.hdekker.ai_workflow.app.pipeline.RegexInputFileFilter.FilterResult;
import com.hdekker.ai_workflow.pipeline.domain.PipelinePrompt;

public record PromptResponse(PipelinePrompt prompt, String fileName, String fileContents, String response) {

	public String createOutputFileName() {
		
		FilterResult match = RegexInputFileFilter.matches(fileName, prompt.fileInputRegex());
		match.groups().put("title", prompt().title());
		return OutputFilenameTemplate.getName(prompt.outputFilenameTemplate(), match);
		
	}

}

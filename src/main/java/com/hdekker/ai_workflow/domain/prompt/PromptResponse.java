package com.hdekker.ai_workflow.domain.prompt;

import com.hdekker.ai_workflow.domain.agent.AgentDefinition;
import com.hdekker.ai_workflow.domain.shared.OutputFilenameTemplate;
import com.hdekker.ai_workflow.domain.shared.RegexInputFileFilter;
import com.hdekker.ai_workflow.domain.shared.RegexInputFileFilter.FilterResult;

public record PromptResponse(AgentDefinition prompt, String fileName, String fileContents, String response) {

	public String createOutputFileName() {
		
		FilterResult match = RegexInputFileFilter.matches(fileName, prompt.fileInputRegex());
		match.groups().put("title", prompt().title());
		return OutputFilenameTemplate.getName(prompt.outputFilenameTemplate(), match);
		
	}

}

package com.hdekker.ai_workflow.domain.prompt;

import java.util.HashMap;
import java.util.Map;

import com.hdekker.ai_workflow.domain.agent.AgentDefinition;
import com.hdekker.ai_workflow.domain.shared.OutputFilenameTemplate;
import com.hdekker.ai_workflow.domain.shared.RegexInputFileFilter;
import com.hdekker.ai_workflow.domain.shared.RegexInputFileFilter.FilterResult;

public record PromptResponse(AgentDefinition prompt, String fileName, String fileContents, String response) {

	/**
	 * Creates an output file name by combining the regex match groups with the
	 * agent title. Does NOT mutate the shared groups map from {@link FilterResult}.
	 *
	 * @return the resolved output file name
	 */
	public String createOutputFileName() {
		FilterResult match = RegexInputFileFilter.matches(fileName, prompt.fileInputRegex());
		// Copy groups into a new mutable map to avoid mutating the shared FilterResult map
		Map<String, String> merged = new HashMap<>();
		if (match.groups() != null) {
			merged.putAll(match.groups());
		}
		merged.put("title", prompt().title());
		// Create a new FilterResult with the merged map — original is untouched
		FilterResult enriched = new FilterResult(match.matches(), merged);
		return OutputFilenameTemplate.getName(prompt.outputFilenameTemplate(), enriched);
	}

}

package com.hdekker.ai_workflow.pipeline.domain;

import com.hdekker.ai_workflow.app.pipeline.RegexInputFileFilter;

/**
 *  To capture configuration of a prompt for
 *  a pipeline
 * 
 */
public record AgentDefinition(
		String fileInputRegex,
		String title,
		String body,
		String agentType,
		String outputStructure,
		String outputFilenameTemplate
		) {

	public Boolean inputRegexMatches(String fileURL) {
		return RegexInputFileFilter.matches(fileURL, fileInputRegex).matches();
	}

}

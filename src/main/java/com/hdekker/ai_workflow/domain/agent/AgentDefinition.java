package com.hdekker.ai_workflow.domain.agent;

import com.hdekker.ai_workflow.domain.shared.RegexInputFileFilter;

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
		String outputFilenameTemplate,
		String targetDirectory
		) {

	public Boolean inputRegexMatches(String fileURL) {
		return RegexInputFileFilter.matches(fileURL, fileInputRegex).matches();
	}

}

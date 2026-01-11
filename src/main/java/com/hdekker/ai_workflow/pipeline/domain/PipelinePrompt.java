package com.hdekker.ai_workflow.pipeline.domain;

import com.hdekker.ai_workflow.app.pipeline.RegexInputFileFilter;

/**
 *  To capture configuration of a prompt for
 *  a pipeline
 *  
 *  // TODO split API from prompt object. Don't need event or type in prompt
 * 
 */
public record PipelinePrompt(
		String event,
		String fileInputRegex,
		String title,
		String type,
		String body,
		String outputStructure,
		String outputFilenameTemplate
		) {

	public Boolean inputRegexMatches(String fileURL) {
		return RegexInputFileFilter.matches(fileURL, fileInputRegex).matches();
	}

}

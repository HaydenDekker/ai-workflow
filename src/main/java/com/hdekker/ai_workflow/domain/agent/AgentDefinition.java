package com.hdekker.ai_workflow.domain.agent;

import com.fasterxml.jackson.annotation.JsonAlias;

import com.hdekker.ai_workflow.domain.shared.RegexInputFileFilter;

/**
 * To capture configuration of a prompt for a pipeline.
 *
 * <p>Uses {@link AgentType} enum for the agent processing strategy instead of
 * raw strings, providing compile-time safety and eliminating magic-string
 * comparisons ("Map", "Reduction", "Split").</p>
 */
public record AgentDefinition(
		String fileInputRegex,
		String title,
		String body,
		@JsonAlias("agentType") AgentType agentType,
		String outputStructure,
		String outputFilenameTemplate,
		String targetDirectory
		) {

	public Boolean inputRegexMatches(String fileURL) {
		return RegexInputFileFilter.matches(fileURL, fileInputRegex).matches();
	}

}

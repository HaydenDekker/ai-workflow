package com.hdekker.ai_workflow.domain.agent;

import java.util.Objects;
import java.util.regex.Pattern;

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

	/**
	 * Canonical constructor with validation.
	 *
	 * @param fileInputRegex the regex pattern to match input files (must be non-null, non-empty, valid)
	 * @param title the agent title (must be non-null)
	 * @param body the system prompt body (must be non-null)
	 * @param agentType the agent processing strategy (must be non-null)
	 * @param outputStructure the output structure description (nullable)
	 * @param outputFilenameTemplate the filename template (nullable)
	 * @param targetDirectory the target directory path (nullable)
	 * @throws NullPointerException if required fields are null
	 * @throws IllegalArgumentException if fileInputRegex is empty or invalid
	 */
	public AgentDefinition {
		Objects.requireNonNull(fileInputRegex, "fileInputRegex must not be null");
		Objects.requireNonNull(title, "title must not be null");
		Objects.requireNonNull(body, "body must not be null");
		Objects.requireNonNull(agentType, "agentType must not be null");

		if (fileInputRegex.isEmpty()) {
			throw new IllegalArgumentException("fileInputRegex must not be empty");
		}
		try {
			Pattern.compile(fileInputRegex);
		} catch (java.util.regex.PatternSyntaxException e) {
			throw new IllegalArgumentException("Invalid regex pattern '" + fileInputRegex + "': " + e.getMessage(), e);
		}
	}

	public Boolean inputRegexMatches(String fileURL) {
		return RegexInputFileFilter.matches(fileURL, fileInputRegex).matches();
	}

}

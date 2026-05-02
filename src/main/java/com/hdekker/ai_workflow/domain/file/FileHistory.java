package com.hdekker.ai_workflow.domain.file;

import java.util.Optional;

import com.hdekker.ai_workflow.domain.prompt.PromptRequest;

/**
 * Represents the history of a file — current and previous metadata.
 * <p>
 * Used by the scanner to detect changes via hash comparison,
 * and by the pipeline to produce {@link PromptRequest} objects.
 * Pure domain record — no I/O, no framework dependencies.
 */
public record FileHistory(
		FileMetadata currentFile,
		Optional<FileMetadata> previousFile
		) {

	/**
	 * Returns true if the current file's hash matches the previous file's hash.
	 */
	public boolean hashMatches() {
		return previousFile.filter(fm -> fm.hash().equals(currentFile.hash()))
				.isPresent();
	}

	/**
	 * Converts the current file into a prompt request for the pipeline.
	 */
	public PromptRequest to() {
		return new PromptRequest(currentFile().body(), currentFile().url());
	}
}

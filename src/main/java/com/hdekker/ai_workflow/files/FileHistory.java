package com.hdekker.ai_workflow.files;

import java.util.Optional;

import com.hdekker.ai_workflow.files.domain.FileMetadata;
import com.hdekker.ai_workflow.prompt.PromptRequest;

public record FileHistory(
		FileMetadata currentFile,
		Optional<FileMetadata> previousFile
		) {
	
		public boolean hashMatches() {
			return previousFile.filter(fm->fm.hash().equals(currentFile.hash()))
					.isPresent();
		}
		
		public PromptRequest to() {
			return new PromptRequest(currentFile().body(), currentFile().url());
		}

}

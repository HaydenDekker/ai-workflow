package com.hdekker.ai_workflow.files;

import java.util.Optional;

import com.hdekker.ai_workflow.files.domain.FileMetadata;

public record FileHistory(
		FileMetadata currentFile,
		Optional<FileMetadata> previousFile
		) {
	
		public boolean hashMatches() {
			return previousFile.filter(fm->fm.hash().equals(currentFile.hash()))
					.isPresent();
		}

}

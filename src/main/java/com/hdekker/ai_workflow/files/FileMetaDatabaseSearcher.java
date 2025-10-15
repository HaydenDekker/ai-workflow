package com.hdekker.ai_workflow.files;

import java.util.Optional;

import com.hdekker.ai_workflow.files.domain.FileMetadata;

public interface FileMetaDatabaseSearcher {
	Optional<FileMetadata> findById(String url);
}

package com.hdekker.ai_workflow.adapter.outbound.file;

import java.util.Optional;

import com.hdekker.ai_workflow.domain.file.FileMetadata;

public interface FileMetaDatabaseSearcher {
	Optional<FileMetadata> findById(String url);
}

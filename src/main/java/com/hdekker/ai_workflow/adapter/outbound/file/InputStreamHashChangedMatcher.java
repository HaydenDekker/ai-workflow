package com.hdekker.ai_workflow.adapter.outbound.file;

import com.hdekker.ai_workflow.domain.file.FileHistory;
import com.hdekker.ai_workflow.domain.file.FileMetadata;

/***
 * To determine if a resource has changes warranting 
 * an update to downstream dependencies.
 * 
 */
public interface InputStreamHashChangedMatcher {
	FileHistory matches(FileMetadata file);
}

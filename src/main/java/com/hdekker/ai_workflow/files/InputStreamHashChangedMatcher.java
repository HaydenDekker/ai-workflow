package com.hdekker.ai_workflow.files;

import com.hdekker.ai_workflow.domain.file.FileMetadata;

/***
 * To determine if a resource has changes warranting 
 * an update to downstream dependencies.
 * 
 */
public interface InputStreamHashChangedMatcher {
	FileHistory matches(FileMetadata file);
}

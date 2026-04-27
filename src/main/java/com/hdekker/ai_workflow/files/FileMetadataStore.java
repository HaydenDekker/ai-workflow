package com.hdekker.ai_workflow.files;

import java.util.Optional;

import com.hdekker.ai_workflow.files.domain.FileMetadata;

/**
 * General interface for file metadata storage and retrieval.
 * <p>
 * Used by {@link NativeFileWatcher} for change detection and persistence,
 * allowing both real implementations and test doubles.
 */
public interface FileMetadataStore {

	/**
	 * Find a file metadata entry by URL.
	 *
	 * @param url the file URL/path
	 * @return the metadata if found
	 */
	Optional<FileMetadata> findById(String url);

	/**
	 * Save file metadata.
	 *
	 * @param file the file metadata to save
	 */
	void save(FileMetadata file);
}

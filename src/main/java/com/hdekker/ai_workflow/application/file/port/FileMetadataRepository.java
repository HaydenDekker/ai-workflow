package com.hdekker.ai_workflow.application.file.port;

import java.util.Optional;

import com.hdekker.ai_workflow.domain.file.FileMetadata;

/**
 * Port interface for file metadata storage and retrieval.
 * <p>
 * The application layer uses this port for change detection — comparing
 * current file hashes against stored metadata. Infrastructure adapters
 * (database-backed, in-memory, etc.) implement this port.
 */
public interface FileMetadataRepository {

    /**
     * Find a file metadata entry by its unique URL/path identifier.
     *
     * @param url the file URL/path
     * @return the metadata if found
     */
    Optional<FileMetadata> findById(String url);

    /**
     * Save file metadata. Creates or overwrites the existing entry.
     *
     * @param file the file metadata to save
     */
    void save(FileMetadata file);
}

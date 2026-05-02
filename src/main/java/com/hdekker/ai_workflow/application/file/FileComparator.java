package com.hdekker.ai_workflow.application.file;

import java.util.Optional;

import com.hdekker.ai_workflow.application.file.port.FileMetadataRepository;
import com.hdekker.ai_workflow.domain.file.FileHistory;
import com.hdekker.ai_workflow.domain.file.FileMetadata;

/**
 * Compares file metadata against stored metadata to detect changes.
 * <p>
 * Application-layer utility — depends on the {@link FileMetadataRepository}
 * port rather than a concrete infrastructure implementation.
 */
public class FileComparator {

    private final FileMetadataRepository repository;

    public FileComparator(FileMetadataRepository repository) {
        this.repository = repository;
    }

    /**
     * Compares the given file metadata against stored metadata.
     * Returns a {@link FileHistory} with the previous file (if found).
     *
     * @param file the current file metadata
     * @return a FileHistory with current and previous metadata
     */
    public FileHistory matches(FileMetadata file) {
        Optional<FileMetadata> previousFile = repository.findById(file.url());
        return new FileHistory(file, previousFile);
    }
}

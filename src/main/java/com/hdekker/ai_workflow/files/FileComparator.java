package com.hdekker.ai_workflow.files;

import com.hdekker.ai_workflow.domain.file.FileMetadata;

import java.util.Optional;

/**
 * Re-export of {@link com.hdekker.ai_workflow.application.file.FileComparator} for backward compatibility.
 * <p>
 * The canonical definition has moved to {@code application.file.FileComparator}.
 *
 * @deprecated Use {@link com.hdekker.ai_workflow.application.file.FileComparator} directly.
 */
@Deprecated
public class FileComparator {

    private final FileMetadataStore repository;

    public FileComparator(FileMetadataStore repository) {
        this.repository = repository;
    }

    public FileHistory matches(FileMetadata file) {
        Optional<FileMetadata> previousFile = repository.findById(file.url());
        return new FileHistory(file, previousFile);
    }
}

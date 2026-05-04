package com.hdekker.ai_workflow.files;

import java.util.Optional;

import com.hdekker.ai_workflow.domain.file.FileMetadata;
import com.hdekker.ai_workflow.domain.prompt.PromptRequest;

/**
 * Re-export of {@link com.hdekker.ai_workflow.domain.file.FileHistory} for backward compatibility.
 * <p>
 * The canonical definition has moved to {@code domain.file.FileHistory}.
 * This stub re-exports the domain record to avoid breaking existing imports.
 *
 * @deprecated Use {@link com.hdekker.ai_workflow.domain.file.FileHistory} directly.
 */
@Deprecated
public record FileHistory(
        FileMetadata currentFile,
        Optional<FileMetadata> previousFile
) {

    public boolean hashMatches() {
        return previousFile.filter(fm -> fm.hash().equals(currentFile.hash())).isPresent();
    }

    public PromptRequest to() {
        return new PromptRequest(currentFile().body(), currentFile().url());
    }
}

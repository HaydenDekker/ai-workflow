package com.hdekker.ai_workflow.files;

/**
 * Re-export of {@link com.hdekker.ai_workflow.domain.shared.FileHash} for backward compatibility.
 * <p>
 * The canonical definition has moved to {@code domain.shared.FileHash}.
 *
 * @deprecated Use {@link com.hdekker.ai_workflow.domain.shared.FileHash} directly.
 */
@Deprecated
public final class FileHash {
    private FileHash() {}

    public static String hash(String content) {
        return com.hdekker.ai_workflow.domain.shared.FileHash.hash(content);
    }
}

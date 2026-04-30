package com.hdekker.ai_workflow.usecases;

import java.nio.file.Path;

/**
 * Raw file event emitted by {@link com.hdekker.ai_workflow.files.NativeFileWatcherAdapter}.
 * <p>
 * Contains the file path, content, and event type. Business logic (hashing, comparison,
 * history creation) is applied by {@link Scanner} when subscribing to these events.
 */
public record RawFileEvent(Path path, String content, RawFileEventType eventType) {

    /**
     * The type of file system event.
     */
    public enum RawFileEventType {
        /** File created or modified (content available). */
        CREATE,
        /** File modified (content available). */
        MODIFY,
        /** File deleted (content is null). */
        DELETE
    }

    /**
     * Create a CREATE event.
     */
    public RawFileEvent(Path path, String content) {
        this(path, content, RawFileEventType.CREATE);
    }
}

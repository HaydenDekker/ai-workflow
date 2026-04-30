package com.hdekker.ai_workflow.usecases;

import java.nio.file.Path;

/**
 * Raw file event emitted by {@link com.hdekker.ai_workflow.files.NativeFileWatcherAdapter}.
 * <p>
 * Contains the file path and content. Business logic (hashing, comparison, history
 * creation) is applied by {@link Scanner} when subscribing to these events.
 */
public record RawFileEvent(Path path, String content) {}

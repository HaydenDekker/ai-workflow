package com.hdekker.ai_workflow.application.file.port;

/**
 * Port interface for counting files in a directory.
 * <p>
 * Declares the filesystem operation the application layer needs
 * for computing file counts. Infrastructure adapters (JPA-backed,
 * real filesystem, etc.) implement this port.
 * <p>
 * Previously lived in {@code usecases.FileCounter} as a general-purpose
 * interface — now correctly placed as an application-layer port.
 */
public interface FileCounterPort {

    /**
     * Count the number of regular files under the given path (recursively).
     *
     * @param path the directory path to count files in
     * @return the number of regular files, or 0 if the path cannot be walked
     */
    long countFiles(String path);
}

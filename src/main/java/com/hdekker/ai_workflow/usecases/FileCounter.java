package com.hdekker.ai_workflow.usecases;

/**
 * Interface for counting files in a given directory path.
 * <p>
 * Abstraction allows the use case to remain testable without touching the real filesystem.
 */
public interface FileCounter {

    /**
     * Count the number of regular files under the given path (recursively).
     *
     * @param path the directory path to count files in
     * @return the number of regular files, or 0 if the path cannot be walked
     */
    long countFiles(String path);
}

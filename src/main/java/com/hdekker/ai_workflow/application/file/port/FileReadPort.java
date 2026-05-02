package com.hdekker.ai_workflow.application.file.port;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Port interface for reading file content.
 * <p>
 * The application layer reads file content through this port without
 * depending on the underlying filesystem adapter. Used by the scanner
 * for content-based processing (hashing, comparison).
 */
public interface FileReadPort {

    /**
     * Read the full content of a file as a string.
     *
     * @param path the file path to read
     * @return the file content
     * @throws IOException if the file cannot be read
     */
    String readContent(Path path) throws IOException;

    /**
     * Check if a path is a regular file (not a directory).
     *
     * @param path the path to check
     * @return true if the path is a regular file
     */
    boolean isRegularFile(Path path);

    /**
     * Walk the directory tree and collect all regular file paths.
     *
     * @param directory the root directory to walk
     * @return a list of all regular file paths under the directory
     * @throws IOException if the directory cannot be walked
     */
    java.util.List<Path> walkFiles(Path directory) throws IOException;
}

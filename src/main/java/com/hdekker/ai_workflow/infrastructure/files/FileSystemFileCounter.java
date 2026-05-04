package com.hdekker.ai_workflow.infrastructure.files;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hdekker.ai_workflow.usecases.FileCounter;

import org.springframework.stereotype.Component;

/**
 * Production {@link FileCounter} implementation that walks the real filesystem.
 */
@Component
public class FileSystemFileCounter implements FileCounter {

    private static final Logger log = LoggerFactory.getLogger(FileSystemFileCounter.class);

    @Override
    public long countFiles(String path) {
        try {
            return Files.walk(Path.of(path).toAbsolutePath())
                    .filter(Files::isRegularFile)
                    .count();
        } catch (IOException e) {
            log.warn("Failed to count files at path {}: {}", path, e.getMessage());
            return 0;
        }
    }
}

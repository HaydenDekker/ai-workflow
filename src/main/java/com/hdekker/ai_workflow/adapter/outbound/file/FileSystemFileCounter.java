package com.hdekker.ai_workflow.adapter.outbound.file;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hdekker.ai_workflow.application.file.port.FileCounterPort;

import org.springframework.stereotype.Component;

/**
 * Production {@link FileCounter} implementation that walks the real filesystem.
 */
@Component
public class FileSystemFileCounter implements FileCounterPort {

    private static final Logger log = LoggerFactory.getLogger(FileSystemFileCounter.class);

    @Override
    public long countFiles(String path) {
        if (path == null || path.isBlank()) {
            log.debug("countFiles called with null/blank path — returning 0");
            return 0;
        }
        try {
            Path dir = Path.of(path).toAbsolutePath();
            if (!Files.exists(dir)) {
                log.warn("Output directory does not exist: {} — creating it", dir);
                Files.createDirectories(dir);
            }
            return Files.walk(dir)
                    .filter(Files::isRegularFile)
                    .count();
        } catch (IOException e) {
            log.error("Failed to count files at path {}: {}", path, e.getMessage(), e);
            return 0;
        }
    }
}

package com.hdekker.ai_workflow.adapter.outbound.file;

import java.nio.file.Files;
import java.nio.file.Path;

import com.hdekker.ai_workflow.application.agent.port.DirectoryValidationPort;

import org.springframework.stereotype.Component;

/**
 * Validates target directory paths for agent definitions.
 * <p>
 * Rejects null, blank, relative, non-existent, non-directory, and unreadable paths.
 * Used by {@code AgentLifecycleService}, {@code AgentController}, and
 * {@code AgentInfoService} to prevent silent fallbacks to {@code /tmp}.
 */
@Component
public class TargetDirectoryValidator implements DirectoryValidationPort {

    @Override
    public ValidationResult validate(String path) {
        if (path == null || path.isBlank()) {
            return ValidationResult.failure("targetDirectory is required");
        }

        Path p = Path.of(path);

        if (!p.isAbsolute()) {
            return ValidationResult.failure("targetDirectory must be an absolute path");
        }

        if (!Files.exists(p)) {
            return ValidationResult.failure("targetDirectory does not exist: " + path);
        }

        if (!Files.isDirectory(p)) {
            return ValidationResult.failure("targetDirectory is not a directory: " + path);
        }

        if (!Files.isReadable(p)) {
            return ValidationResult.failure("targetDirectory is not readable: " + path);
        }

        return ValidationResult.success();
    }

}

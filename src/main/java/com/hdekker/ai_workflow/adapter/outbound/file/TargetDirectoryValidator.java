package com.hdekker.ai_workflow.adapter.outbound.file;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Validates target directory paths for agent definitions.
 * <p>
 * Rejects null, blank, relative, non-existent, non-directory, and unreadable paths.
 * Used by {@code AgentLifecycleUseCase}, {@code AgentRestController}, and
 * {@code AgentInfoService} to prevent silent fallbacks to {@code /tmp}.
 */
public class TargetDirectoryValidator {

    /**
     * Validates a target directory path string.
     *
     * @param path the path to validate (may be null)
     * @return a {@link ValidationResult} indicating success or failure reason
     */
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

    /**
     * Immutable result of a target directory validation.
     */
    public record ValidationResult(boolean valid, String reason) {

        /**
         * Returns a valid (success) result.
         */
        public static ValidationResult success() {
            return new ValidationResult(true, null);
        }

        /**
         * Returns an invalid (failure) result with the given reason.
         *
         * @param reason human-readable explanation of why validation failed
         */
        public static ValidationResult failure(String reason) {
            return new ValidationResult(false, reason);
        }
    }
}

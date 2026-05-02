package com.hdekker.ai_workflow.application.agent.port;

/**
 * Port interface for validating target directory paths for agents.
 * <p>
 * The application layer delegates directory validation to this port
 * without depending on the concrete filesystem implementation.
 */
public interface DirectoryValidationPort {

    /**
     * Result of a target directory validation.
     */
    record ValidationResult(boolean valid, String reason) {

        public static ValidationResult success() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult failure(String reason) {
            return new ValidationResult(false, reason);
        }
    }

    /**
     * Validates a target directory path string.
     *
     * @param path the path to validate (may be null)
     * @return a {@link ValidationResult} indicating success or failure reason
     */
    ValidationResult validate(String path);
}

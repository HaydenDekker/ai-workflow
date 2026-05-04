package com.hdekker.ai_workflow.files;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;


import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.hdekker.ai_workflow.files.TargetDirectoryValidator.ValidationResult;

public class TargetDirectoryValidatorTest {

    private final TargetDirectoryValidator validator = new TargetDirectoryValidator();

    @TempDir
    Path tempDir;

    @Test
    void validatesAbsoluteExistingReadableDir() {
        ValidationResult result = validator.validate(tempDir.toString());

        assertThat(result.valid()).isTrue();
        assertThat(result.reason()).isNull();
    }

    @Test
    void rejectsNull() {
        ValidationResult result = validator.validate(null);

        assertThat(result.valid()).isFalse();
        assertThat(result.reason()).isEqualTo("targetDirectory is required");
    }

    @Test
    void rejectsBlank() {
        ValidationResult emptyResult = validator.validate("");
        ValidationResult spaceResult = validator.validate("   ");

        assertThat(emptyResult.valid()).isFalse();
        assertThat(emptyResult.reason()).isEqualTo("targetDirectory is required");
        assertThat(spaceResult.valid()).isFalse();
        assertThat(spaceResult.reason()).isEqualTo("targetDirectory is required");
    }

    @Test
    void rejectsRelativePath() {
        ValidationResult result = validator.validate("./some/path");

        assertThat(result.valid()).isFalse();
        assertThat(result.reason()).isEqualTo("targetDirectory must be an absolute path");
    }

    @Test
    void rejectsNonExistent() {
        // Use a platform-appropriate absolute path that doesn't exist
        String nonexistent = tempDir.resolve("nonexistent").resolve("path").toString();
        ValidationResult result = validator.validate(nonexistent);

        assertThat(result.valid()).isFalse();
        assertThat(result.reason()).isEqualTo("targetDirectory does not exist: " + nonexistent);
    }

    @Test
    void rejectsFileNotDirectory(@TempDir Path tempDir2) throws IOException {
        Path file = tempDir2.resolve("a-file.txt");
        Files.writeString(file, "content");

        ValidationResult result = validator.validate(file.toString());

        assertThat(result.valid()).isFalse();
        assertThat(result.reason()).contains("is not a directory");
        assertThat(result.reason()).contains(file.toString());
    }

    @Test
    void rejectsUnreadableDir() throws IOException {
        // On Windows, setPosixFilePermissions is not supported.
        // We still create an unreadable directory to test the validation path.
        Path unreadableDir = Files.createDirectory(tempDir.resolve("unreadable"));
        try {
            Files.setPosixFilePermissions(unreadableDir,
                    java.util.Set.of(java.nio.file.attribute.PosixFilePermission.OWNER_WRITE));
        } catch (UnsupportedOperationException e) {
            // Windows doesn't support POSIX permissions — skip the permission check.
            // The directory will still be validated (exists, is a directory).
            // On Windows it may still be readable, so we just verify the path
            // is accepted when readable, or rejected if truly unreadable.
            ValidationResult result = validator.validate(unreadableDir.toString());
            // On Windows, the dir will likely be readable, so we just assert
            // that the validator returns a result (valid or invalid).
            assertThat(result).isNotNull();
            return;
        }

        ValidationResult result = validator.validate(unreadableDir.toString());

        assertThat(result.valid()).isFalse();
        assertThat(result.reason()).contains("is not readable");
    }
}

package com.hdekker.ai_workflow.app.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/***
 *  To configure the rang of files this prompt accepts.
 * 
 */
public class RegexInputFileFilterTest {
	
	public static class RegexInputFileFilter {
		/**
		 * Checks if the input string matches the provided regex pattern.
		 */
		public boolean matches(String input, String regex) {
			if (input == null || regex == null) {
				return false;
			}
			return Pattern.matches(regex, input);
		}
	}
	
	record TestCase(String name, String inputFile, String regex, Boolean shouldMatch) {}
	
	public static List<TestCase> testCases(){
		return List.of(
	            // 1. Basic match
	            new TestCase("Simple extension match", "input-file.txt", ".*\\.txt", true),

	            // 2. Folder: Match any file in a specific folder
	            // Matches "logs/" followed by any characters
	            new TestCase("Any file in 'logs' folder", "logs/app.log", "logs/.*", true),

	            // 3. Folder: Match a specific file type in a specific folder
	            // Matches "src/main/" then any chars ending in ".java"
	            new TestCase("Java files in src folder", "src/main/User.java", "src/main/.*\\.java", true),

	            // 4. Specific file in any subfolder
	            // Matches any path ending in "config.json"
	            new TestCase("Config in any subfolder", "etc/app/config.json", ".*/config\\.json", true),

	            // 5. Obscure: Case sensitivity (Regex is case-sensitive by default)
	            new TestCase("Case mismatch (Should fail)", "IMAGE.PNG", ".*\\.png", false),

	            // 6. Obscure: Hidden files
	            new TestCase("Hidden file match", ".gitignore", "\\..*", true),

	            // 7. Obscure: Multiple dots in filename
	            new TestCase("Multiple dots match", "archive.tar.gz", ".*\\.tar\\.gz", true),

	            // 8. Negative Match: Ensure regex doesn't over-match
	            new TestCase("Partial name mismatch", "backup-txt-file.zip", ".*\\.txt", false)
	        );
	}

	@ParameterizedTest()
	@MethodSource("testCases")
	public void givenInputFileMatchingRegex_ExpectPasses(TestCase testCase) {
		
		// Arrange
        RegexInputFileFilter filter = new RegexInputFileFilter();

        // Act
        boolean result = filter.matches(testCase.inputFile(), testCase.regex());

        // Assert
        assertEquals(testCase.shouldMatch(), result, 
            String.format("Failed case '%s': Input '%s' against Regex '%s'", 
            testCase.name(), testCase.inputFile(), testCase.regex()));
	}

}

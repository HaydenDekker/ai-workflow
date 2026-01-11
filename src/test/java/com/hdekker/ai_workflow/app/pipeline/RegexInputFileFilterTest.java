package com.hdekker.ai_workflow.app.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import com.hdekker.ai_workflow.app.pipeline.RegexInputFileFilter.FilterResult;

/***
 *  To configure the rang of files this prompt accepts.
 * 
 */
public class RegexInputFileFilterTest {
	

	public record TestCase(
	        String testName, 
	        String inputFile, 
	        String regex, 
	        boolean shouldMatch, 
	        String expectedPath, 
	        String expectedName
	    ) {}

	    public static List<TestCase> testCases() {
	        return List.of(
	            // 1. Full Capture: Path and Name
	            new TestCase("Full capture", "usr/bin/java", "(?<path>.*/)(?<name>.*)", true, "usr/bin/", "java"),

	            // 2. Path Only: No name group in regex
	            new TestCase("Path only capture", "home/user/docs/readme.txt", "(?<path>.*/).*\\.txt", true, "home/user/docs/", null),

	            // 3. Name Only: No path group in regex
	            new TestCase("Name only capture", "home/user/docs/readme.txt", ".*/(?<name>.*\\.txt)", true, null, "readme.txt"),

	            // 4. Capture Path in the middle (Obscure)
	            new TestCase("Path in middle", "cloud/storage/v1/bucket/file.png", "cloud/(?<path>.*)/bucket/(?<name>.*)", true, "storage/v1", "file.png"),

	            // 5. No Groups: Just a boolean match
	            new TestCase("No groups captured", "simple-file.txt", ".*\\.txt", true, null, null),

	            // 6. Fail Match: Groups should be null
	            new TestCase("Failed match", "wrong-extension.jpg", ".*\\.txt", false, null, null),
	            
	            new TestCase("Simple extension match", "input-file.txt", ".*\\.txt", true, null, null),

	            // 2. Folder: Match any file in a specific folder (Capturing the folder as path)
	            new TestCase("Any file in 'logs' folder", "logs/app.log", "(?<path>logs/)(?<name>.*)", true, "logs/", "app.log"),

	            // 3. Folder: Match specific file type (Capturing complex path)
	            new TestCase("Java files in src folder", "src/main/User.java", "(?<path>src/main/)(?<name>.*\\.java)", true, "src/main/", "User.java"),

	            // 4. Specific file in any subfolder (Capturing path anywhere)
	            new TestCase("Config in any subfolder", "etc/app/config.json", "(?<path>.*/)(?<name>config\\.json)", true, "etc/app/", "config.json"),

	            // 5. Case sensitivity (Regex failure)
	            new TestCase("Case mismatch (Should fail)", "IMAGE.PNG", ".*\\.png", false, null, null),

	            // 6. Hidden files (Name only)
	            new TestCase("Hidden file match", ".gitignore", "(?<name>\\..*)", true, null, ".gitignore"),

	            // 7. Multiple dots (Path and Name split)
	            new TestCase("Multiple dots match", "backups/2024/archive.tar.gz", "(?<path>.*/)(?<name>.*\\.tar\\.gz)", true, "backups/2024/", "archive.tar.gz"),

	            // 8. Negative Match
	            new TestCase("Partial name mismatch", "backup-txt-file.zip", ".*\\.txt", false, null, null),
	            
	            // 9. Obscure: Path in the middle, name at the end
	            new TestCase("Path in middle of URL", "https://cdn.com/assets/v1/img/hero.jpg", ".*/(?<path>assets/.*/img)/(?<name>.*)", true, "assets/v1/img", "hero.jpg")
	       
	        );
	    }

	    @ParameterizedTest(name = "{index}: {0}")
	    @MethodSource("testCases")
	    public void givenInputFileMatchingRegex_ExpectPasses(TestCase testCase) {

	        FilterResult result = RegexInputFileFilter.matches(testCase.inputFile(), testCase.regex());

	        // Assert basic match
	        assertEquals(testCase.shouldMatch(), result.matches(), "Match status mismatch for: " + testCase.testName());

	        // Assert captured groups
	        if (testCase.shouldMatch()) {
	            assertEquals(testCase.expectedPath(), result.path(), "Path group mismatch for: " + testCase.testName());
	            assertEquals(testCase.expectedName(), result.name(), "Name group mismatch for: " + testCase.testName());
	        }
	    }

}

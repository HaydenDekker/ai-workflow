package com.hdekker.ai_workflow.domain.shared;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import com.hdekker.ai_workflow.domain.shared.RegexInputFileFilter.FilterResult;

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
	        String expectedName,
	        String expectedExtension
	    ) {}

	    public static List<TestCase> testCases() {
	    	return List.of(
	                // 1. Full Capture: Path, Name, and Ext
	                new TestCase("Full capture", "usr/bin/java", "(?<path>.*/)(?<name>.*)", true, "usr/bin/", "java", ""),
	                
	                // 2. Capture with Extension
	                new TestCase("Capture with ext", "logs/app.log", "(?<path>.*/)(?<name>.*)\\.(?<ext>.*)", true, "logs/", "app", "log"),

	                // 3. Name Only (Fixed with optional non-capturing group for path)
	                new TestCase("Name only capture not path", "readme.txt", "(?:.*/)?(?<name>.*\\.txt)", true, "", "readme.txt", ""),

	                // 4. Capture Path in middle, name, and ext
	                new TestCase("Path in middle", "cloud/storage/v1/bucket/file.png", "cloud/(?<path>.*)/bucket/(?<name>.*)\\.(?<ext>.*)", true, "storage/v1", "file", "png"),

	                // 5. Multiple dots in extension
	                new TestCase("Multiple dots match", "backups/2024/archive.tar.gz", "(?<path>.*/)(?<name>.*)\\.(?<ext>tar\\.gz)", true, "backups/2024/", "archive", "tar.gz"),

	                // 6. Hidden files (No path, name starts with dot)
	                new TestCase("Hidden file match", ".gitignore", "(?<name>\\.(?<ext>.*))", true, "", ".gitignore", "gitignore"),

	                // 7. Case sensitivity failure
	                new TestCase("Case mismatch (Should fail)", "IMAGE.PNG", ".*\\.png", false, "", "", ""),

	                // 8. Negative Match
	                new TestCase("Partial name mismatch", "backup-txt-file.zip", ".*\\.txt", false, "", "", ""),

	                // 9. URL Convention
	                new TestCase("URL breakdown", "https://cdn.com/assets/img/hero.jpg", ".*/(?<path>assets(?:/.*)?/img)/(?<name>.*)\\.(?<ext>.*)", true, "assets/img", "hero", "jpg"),
	                
	                new TestCase("Test SOLID file", "C:/Users/hayde/AppData/Local/Temp/junit-3816187321586050836/output/solid-priorty/non-compliance/SOLIDPromptCaller.md", ".*output/solid-priorty/non-compliance/(?<name>.*)\\.(?<ext>md)", true, "", "SOLIDPromptCaller", "md"),
	            
	                new TestCase(
	                        "Normalized Windows Path", 
	                        "other\\output\\solid-priorty\\non-compliance\\SOLIDPromptCaller.md", 
	                        ".*output/solid-priorty/non-compliance/(?<name>.*)\\.(?<ext>md)", 
	                        true, 
	                        "", 
	                        "SOLIDPromptCaller", 
	                        "md"
	                    ),
	                    new TestCase(
	                        "Mixed Slash Path", 
	                        "other/output\\solid-priorty/non-compliance\\SOLIDPromptCaller.md", 
	                        ".*output/solid-priorty/non-compliance/(?<name>.*)\\.(?<ext>md)", 
	                        true, 
	                        "", 
	                        "SOLIDPromptCaller", 
	                        "md"
	                    )
	                
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
	            assertEquals(testCase.expectedExtension(), result.ext(), "Extension mismatch: " + testCase.testName());
	        }
	    }

}

package com.hdekker.ai_workflow.app.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import com.hdekker.ai_workflow.app.pipeline.RegexInputFileFilter.FilterResult;

/***
 * To configure the destination for the output of the prompt
 */
public class OutputFilenameTemplateTest {
	
	
	public static class OutputFilenameTemplate {
        /**
         * Replaces placeholders in the format ${groupName} with 
         * corresponding values from the FilterResult.
         */
        public static String getName(String template, FilterResult matchResult) {
            String result = template;
            
            // Regex to find placeholders like ${name} or ${path}
            Pattern placeholderPattern = Pattern.compile("\\$\\{(\\w+)\\}");
            Matcher matcher = placeholderPattern.matcher(template);

            while (matcher.find()) {
            	
                String placeholder = matcher.group(0); // e.g., ${name}
                String groupName = matcher.group(1);    // e.g., name
                
                // Retrieve the value from the matchResult (RegexInputFileFilter output)
                String replacement = matchResult.groups().get(groupName); 
                
                if (replacement != null) {
                    result = result.replace(placeholder, replacement);
                }
                
            }
            return result;
        }
    }
	
	public record TestCase(String testName, String fileName, String inputRegex, String outputTemplate, String expectedOutputFileName) {}

    @Test
    public void givenInputFilenameRegex_ExpectOutputfileTemplateUsesName() {
    	
        String filename = "usr/bin/java-class.java";
        String inputRegex = "(?<path>.*/)(?<name>.*)";
        
        FilterResult matchResult = RegexInputFileFilter.matches(filename, inputRegex);
        
        assertThat(matchResult.matches()).isTrue();
        assertThat(matchResult.name()).isEqualTo("java-class.java");
        
        String outputFileTemplate = "output/${path}${name}.txt";
        
        // Act
        String outputFileName = OutputFilenameTemplate.getName(outputFileTemplate, matchResult);
        
        // Assert
        assertThat(outputFileName)
            .isEqualTo("output/usr/bin/java-class.java.txt");
    }
	

}

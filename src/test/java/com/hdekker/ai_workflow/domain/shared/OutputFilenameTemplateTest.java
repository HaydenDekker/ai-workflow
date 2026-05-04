package com.hdekker.ai_workflow.domain.shared;

import static org.assertj.core.api.Assertions.assertThat;


import org.junit.jupiter.api.Test;

import com.hdekker.ai_workflow.domain.shared.RegexInputFileFilter.FilterResult;

/***
 * To configure the destination for the output of the prompt
 */
public class OutputFilenameTemplateTest {

	
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

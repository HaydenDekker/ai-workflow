package com.hdekker.ai_workflow.app.pipeline;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.hdekker.ai_workflow.app.pipeline.RegexInputFileFilter.FilterResult;

public class OutputFilenameTemplate {
	
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

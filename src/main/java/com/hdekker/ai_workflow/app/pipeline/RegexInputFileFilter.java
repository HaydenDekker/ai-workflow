package com.hdekker.ai_workflow.app.pipeline;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RegexInputFileFilter {
	
	public record FilterResult(boolean matches, Map<String, String> groups, String path, String name) {
		
	}
	
	public static FilterResult matches(String input, String regex) {
        if (input == null || regex == null) {
            return new FilterResult(false, null, null, null);
        }

        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(input);
        
        Map<String, String> groups = new HashMap<>();

        if (matcher.matches()) {
            // Extract groups safely, checking if the named groups exist in the regex
            String path = hasGroup(matcher, "path") ? matcher.group("path") : null;
            String name = hasGroup(matcher, "name") ? matcher.group("name") : null;
            
            groups.put("path", matcher.group("path"));
            groups.put("name", matcher.group("name"));
            
            return new FilterResult(true, groups, path, name);
        }

        return new FilterResult(false, groups, null, null);
    }

    private static boolean hasGroup(Matcher matcher, String groupName) {
        try {
            matcher.start(groupName);
            return true;
        } catch (IllegalArgumentException | IllegalStateException e) {
            return false;
        }
    }
}

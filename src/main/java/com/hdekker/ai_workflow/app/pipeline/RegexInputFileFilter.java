package com.hdekker.ai_workflow.app.pipeline;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RegexInputFileFilter {
	
	public record FilterResult(boolean matches, Map<String, String> groups) {
		
		public String path() {
			String path = groups.get("path");
			return (path!=null)? path: "";
		}
		
		public String name() {
			String name = groups.get("name");
			return (name!=null)? name: "";
		}
		
	}
	
	public static FilterResult matches(String input, String regex) {
        if (input == null || regex == null) {
            return new FilterResult(false, null);
        }

        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(input);
        
        Map<String, String> groups = new HashMap<>();

        if (matcher.matches()) {
            // Extract groups safely, checking if the named groups exist in the regex
            if(hasGroup(matcher, "path")){
            	groups.put("path", matcher.group("path"));
            }
            if(hasGroup(matcher, "name")) {
            	groups.put("name", matcher.group("name"));
            }
            
            return new FilterResult(true, groups);
        }

        return new FilterResult(false, groups);
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

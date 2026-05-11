package com.hdekker.ai_workflow.domain.shared;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RegexInputFileFilter {

    private static final Logger log = LoggerFactory.getLogger(RegexInputFileFilter.class);
	
	public record FilterResult(boolean matches, Map<String, String> groups) {
		
		public String path() {
			String path = groups.get("path");
			return (path!=null)? path: "";
		}
		
		public String name() {
			String name = groups.get("name");
			return (name!=null)? name: "";
		}

		public String ext() {
			String ext = groups.get("ext");
			return (ext!=null)? ext: "";
		}
		
	}
	
	public static FilterResult matches(String input, String regex) {
        if (input == null || regex == null) {
            log.info("FILTER: Regex match rejected - input={}, regex={} (null values cause ALL files to be dropped)",
                    input, regex);
            return new FilterResult(false, null);
        }
        
        String normalizedInput = input.replace("\\", "/");

        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(normalizedInput);
        
        Map<String, String> groups = new HashMap<>();

        if (matcher.matches()) {
            // Extract groups safely, checking if the named groups exist in the regex
        	// TODO variables as enum.
            if(hasGroup(matcher, "path")){
            	groups.put("path", matcher.group("path"));
            }
            if(hasGroup(matcher, "name")) {
            	groups.put("name", matcher.group("name"));
            }
            if(hasGroup(matcher, "ext")) {
            	groups.put("ext", matcher.group("ext"));
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

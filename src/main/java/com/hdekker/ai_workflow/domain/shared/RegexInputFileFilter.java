package com.hdekker.ai_workflow.domain.shared;

import java.util.Collections;
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
			if (groups == null) return "";
			String path = groups.get("path");
			return (path!=null)? path: "";
		}
		
		public String name() {
			if (groups == null) return "";
			String name = groups.get("name");
			return (name!=null)? name: "";
		}

		public String ext() {
			if (groups == null) return "";
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
        boolean matched = false;

        if (matcher.matches()) {
            matched = true;
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
        }

        // Return immutable map — callers must copy if they need to mutate
        return new FilterResult(matched, Collections.unmodifiableMap(groups));
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

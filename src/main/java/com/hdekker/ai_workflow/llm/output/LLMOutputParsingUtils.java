package com.hdekker.ai_workflow.llm.output;

public class LLMOutputParsingUtils {
	
	public static String extractJsonContent(String llmResponse) {
	    if (llmResponse == null || llmResponse.isEmpty()) {
	        return "";
	    }

	    final String JSON_MARKER = "```json";

	    // 1. Find the starting index of the JSON marker
	    int startIndex = llmResponse.indexOf(JSON_MARKER);

	    // If the marker is not found, return the original response or handle the error
	    if (startIndex == -1) {
	        // Option 1: Log an error and return the whole string (assuming it's pure JSON)
	        // return llmResponse;
	        
	        // Option 2: Log an error and return an empty string
	        return ""; 
	    }

	    // 2. Calculate the index where the actual JSON content starts
	    // We add the length of the marker to skip past "```json"
	    int contentStartIndex = startIndex + JSON_MARKER.length();

	    // 3. Extract the content starting from this index
	    String jsonBlock = llmResponse.substring(contentStartIndex).trim();

	    // 4. Remove the trailing code fence if it exists (e.g., "```")
	    final String END_MARKER = "```";
	    if (jsonBlock.endsWith(END_MARKER)) {
	        // Remove the last 3 characters ("```")
	        jsonBlock = jsonBlock.substring(0, jsonBlock.length() - END_MARKER.length()).trim();
	    }
	    
	    // 5. Check for any newline or whitespace characters preceding the first JSON brace
	    // This is optional but can help if the LLM puts a newline immediately after ```json
	    return jsonBlock.trim();
	}

}

package com.hdekker.ai_workflow.prompt;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hdekker.ai_workflow.llm.Prompter;
import com.hdekker.ai_workflow.llm.output.SOLIDCompliance;

@Component
public class SOLIDPromtCaller {
	
	Logger log = LoggerFactory.getLogger(SOLIDPromtCaller.class);
	
	@Autowired
	Prompter prompter;
	
	ObjectMapper om = new ObjectMapper();
	
	//String conciseOutput = "The output must be staight to the point. No wordy sentences, just specifc next task. eg. Implement this... Refactor this... and a sentence as to why it should be done.";
	//String conciseOutput = "The output must be a list of json objects with schema, {\"className\":String, \"function\": String, \"description\": String";
	
	public String extractJsonContent(String llmResponse) {
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
	
	public List<SOLIDCompliance> prompt(String fileBody, String outputStructure) {
		
		return prompter.call(PromptConfiguration.SOLID_COMPLAINCE_PROMPT + "\n\r\n\r" + fileBody, outputStructure)
				.collectList()
				.block()
				.stream()
				.reduce((a,b)-> a+b)
				.map(s-> extractJsonContent(s))
				.map(s-> {
					List<SOLIDCompliance> list = List.of();
					try {
						list = om.readValue(s, new TypeReference<List<SOLIDCompliance>>() {});
					} catch (JsonProcessingException e) {
						log.error("Unexpected LLM response " + s);
						e.printStackTrace();
					}
					return list;
				})
				.orElse(List.of());
	
	}

}

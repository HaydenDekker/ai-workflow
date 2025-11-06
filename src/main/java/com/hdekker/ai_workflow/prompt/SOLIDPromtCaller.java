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
	String conciseOutput = "Output json, as {className:String, compliance: YES|NO, principle:String, task:String, reason:String}";
	
	public List<SOLIDCompliance> prompt(String fileBody) {
		
		return prompter.call(PromptConfiguration.SOLID_COMPLAINCE_PROMPT + "\n\r\n\r" + conciseOutput + fileBody)
				.collectList()
				.block()
				.stream()
				.reduce((a,b)-> a+b)
				.map(s->
					s.replaceFirst("(?s)```json\\s*", "")
				    // Remove the closing markdown fence and any trailing whitespace
				    .replaceFirst("(?s)\\s*```$", "")
				    // Trim any remaining leading/trailing whitespace
				    .trim()
				)
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

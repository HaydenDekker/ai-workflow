package com.hdekker.ai_workflow.llm;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class SOLIDPromtCaller {
	
	@Autowired
	OllamaWorld ollamaWorld;
	
	//String prompt = "Given the code, provide a short summary of each function, only if there is an implemeneted function. It must be human readable and as high level as possible.";
	String prompt = 
			"""
			Given the code below, ensure that it complies to SOLID principals. 
			You might not have external context for some classes so the assessment should just reason about the immediate code. 
			If it doesn't immediately break principals state that it is compliant.
			\n\r ---------- \n\r 
			""";
	//String conciseOutput = "The output must be staight to the point. No wordy sentences, just specifc next task. eg. Implement this... Refactor this... and a sentence as to why it should be done.";
	//String conciseOutput = "The output must be a list of json objects with schema, {\"className\":String, \"function\": String, \"description\": String";
	String conciseOutput = "Output json, as {className:String, compliance: YES|NO, principle:String, task:String, reason:String}";
	
	public String prompt(String fileBody) {
		
		String aiResult = ollamaWorld.call(prompt + "\n\r\n\r" + conciseOutput + fileBody)
				.collectList()
				.block()
				.stream()
				.reduce((a,b)-> a+b)
				.orElse("");
		
		return aiResult;
	}

}

package com.hdekker.ai_workflow.llm;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.hdekker.ai_workflow.pipeline.PromptResponse;

@Component
public class GenericPromptCaller {
	
	@Autowired
	Prompter prompter;
	
	public PromptResponse call(String promptTitle, String file, String prompt) {
		
		return prompter.call(file + prompt)
				.reduce((a,b)-> a+b)
				.map(s-> new PromptResponse(promptTitle, s))
				.block();
				
				
		
	}

}

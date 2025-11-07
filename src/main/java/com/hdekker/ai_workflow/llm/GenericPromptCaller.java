package com.hdekker.ai_workflow.llm;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.hdekker.ai_workflow.pipeline.PromptResponse;

@Component
public class GenericPromptCaller {
	
	@Autowired
	Prompter prompter;
	
	public List<PromptResponse> call(String promptTitle, String prompt, String outputStructure, PromptResponseConverter responseConverter) {
		
		return prompter.call(prompt, outputStructure)
				.reduce((a,b)-> a+b)
				.map(s-> new PromptResponse(promptTitle, s))
				.map(s-> responseConverter.convert(s))
				.block();
				
				
		
	}

}

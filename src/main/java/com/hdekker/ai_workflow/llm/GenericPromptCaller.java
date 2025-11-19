package com.hdekker.ai_workflow.llm;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.hdekker.ai_workflow.pipeline.PromptResponse;
import com.hdekker.ai_workflow.pipeline.domain.PipelinePrompt;

@Component
public class GenericPromptCaller {
	
	@Autowired
	Prompter prompter;
	
	public PromptResponse call(PipelinePrompt prompt, String file, String fileName) {
		
		return prompter.call(prompt.body() + "\n\r" + "```code" + file + "\n\r" + "```", prompt.outputStructure())
				.reduce((a,b)-> a+b)
				.map(s-> new PromptResponse(prompt, fileName, file, s))
				.block();
		
	}

}

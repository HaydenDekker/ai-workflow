package com.hdekker.ai_workflow.reports;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hdekker.ai_workflow.database.promptresponse.PromptResponseDatabase;
import com.hdekker.ai_workflow.pipeline.PromptResponse;

import reactor.core.publisher.Flux;

@RestController
public class ReportRestController {
	
	@Autowired
	PromptResponseDatabase promptResponseDatabase;
	
	@GetMapping(path = "/results")
	public Flux<PromptResponse> results(){
		return Flux.fromStream(promptResponseDatabase.responseList().stream());
	}
	
	@GetMapping(path = "/results/{prompt}")
	public Flux<PromptResponse> resultsForPrompt(String prompt){
		return Flux.fromStream(promptResponseDatabase.findAllByPromptTitle(prompt).stream());
	}
	

}

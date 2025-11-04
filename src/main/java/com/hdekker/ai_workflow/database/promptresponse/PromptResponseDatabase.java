package com.hdekker.ai_workflow.database.promptresponse;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.hdekker.ai_workflow.pipeline.PromptResponse;

@Component
public class PromptResponseDatabase {

	@Autowired
	PromptResponseRepository repository;
	
	public List<PromptResponse> responseList(){
		return repository.findAll()
				.stream()
				.map(e-> new PromptResponse(e.response))
				.toList();
	}
	
	public void save(PromptResponse response) {
		
		PromptResponseEntity pre = new PromptResponseEntity();
		pre.setResponse(response.result());
		repository.save(pre);
	}
	
}

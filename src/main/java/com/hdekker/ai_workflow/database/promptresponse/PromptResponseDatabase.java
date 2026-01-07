package com.hdekker.ai_workflow.database.promptresponse;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.hdekker.ai_workflow.pipeline.domain.PipelinePrompt;
import com.hdekker.ai_workflow.prompt.PromptResponse;

@Component
public class PromptResponseDatabase {

	@Autowired
	PromptResponseRepository repository;
	
	PromptResponse parse(PromptResponseEntity e) {
		return new PromptResponse(
				new PipelinePrompt(
						e.getEvent(), 
						e.getPromptTitle(), 
						"Not used",
						e.getPromptInput(), 
						e.getFormat()), 
				e.getFileName(), e.getFile(), e.getResponse());
	}
	
	public List<PromptResponse> responseList(){
		return repository.findAll()
				.stream()
				.map(e-> parse(e))
				.toList();
	}
	
	public void save(PromptResponse response) {
		
		// TODO move into entity as builder
		// TODO duplicating prompt information, probably easy for history though.
		PromptResponseEntity pre = new PromptResponseEntity();
		pre.setResponse(response.response());
		pre.setPromptTitle(response.prompt().title());
		pre.setFileName(response.fileName());
		pre.setPromptInput(response.prompt().body());
		pre.setFormat(response.prompt().outputStructure());
		pre.setEvent(response.prompt().event());
		pre.setFile(response.fileContents());
		repository.save(pre);
		
	}

	public List<PromptResponse> findAllByPromptTitle(String prompt) {
		return repository.findAllByPromptTitle(prompt)
				.stream()
				.map(pre->parse(pre))
				.toList();
	}
	
}

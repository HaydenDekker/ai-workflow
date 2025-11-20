package com.hdekker.ai_workflow.pipeline;

import com.hdekker.ai_workflow.llm.GenericPromptCaller;
import com.hdekker.ai_workflow.prompt.PromptRequest;
import com.hdekker.ai_workflow.prompt.PromptResponse;

import reactor.core.publisher.Flux;

public class LLMReducerAdapter implements LLMAdapter {
	
	final GenericPromptCaller genericPromptCaller;
	
	public LLMReducerAdapter(GenericPromptCaller genericPromptCaller){
		this.genericPromptCaller = genericPromptCaller;
	}

	String latestResponse = "";
	
	@Override
	public Flux<PromptResponse> call(Flux<PromptRequest> request) {
		
		return request.map(pp->{
			
			PromptResponse response = genericPromptCaller.call(
					pp.pipelinePrompt(), 
					"Current Snapshot: \r\n\r\n" + latestResponse + "New aspect for analysis: \r\n\r\n" + pp.file(), 
					pp.fileURL());
			
			latestResponse = response.response();
			
			return response;
			
		});
	}
	
	

}

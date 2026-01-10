package com.hdekker.ai_workflow.pipeline;

import com.hdekker.ai_workflow.llm.Prompter;
import com.hdekker.ai_workflow.pipeline.domain.PipelinePrompt;
import com.hdekker.ai_workflow.prompt.PromptRequest;
import com.hdekker.ai_workflow.prompt.PromptResponse;

import reactor.core.publisher.Flux;

public class LLMReducerAdapter implements LLMAdapter {
	
	final Prompter prompter;
	PipelinePrompt pipelinePrompt;
	
	public LLMReducerAdapter(Prompter prompter, PipelinePrompt pipelinePrompt){
		this.prompter = prompter;
		this.pipelinePrompt = pipelinePrompt;
	}

	String latestResponse = "";
	
	@Override
	public Flux<PromptResponse> call(Flux<PromptRequest> request) {
		
		return request.concatMap(pp->{
			
			String fileContent = "Current Snapshot: \r\n\r\n" + latestResponse + "New aspect for analysis: \r\n\r\n" + pp.file();

			return prompter.call(pipelinePrompt.body() + "\n\r" + "```code" + fileContent + "\n\r" + "```" + "\n\r" + pipelinePrompt.outputStructure())
				.reduce((a,b)-> a+b)
				.map(s-> new PromptResponse(pipelinePrompt, pp.fileURL(), fileContent, s))
					.doOnNext(p -> {
						latestResponse = p.response();
					});
			
		});
	}
	
	

}

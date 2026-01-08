package com.hdekker.ai_workflow.pipeline;

import com.hdekker.ai_workflow.llm.Prompter;
import com.hdekker.ai_workflow.prompt.PromptRequest;
import com.hdekker.ai_workflow.prompt.PromptResponse;

import reactor.core.publisher.Flux;

public class LLMReducerAdapter implements LLMAdapter {
	
	final Prompter prompter;
	
	public LLMReducerAdapter(Prompter prompter){
		this.prompter = prompter;
	}

	String latestResponse = "";
	
	@Override
	public Flux<PromptResponse> call(Flux<PromptRequest> request) {
		
		return request.concatMap(pp->{
			
			String fileContent = "Current Snapshot: \r\n\r\n" + latestResponse + "New aspect for analysis: \r\n\r\n" + pp.file();

			return prompter.call(pp.pipelinePrompt().body() + "\n\r" + "```code" + fileContent + "\n\r" + "```" + "\n\r" + pp.pipelinePrompt().outputStructure())
				.reduce((a,b)-> a+b)
				.map(s-> new PromptResponse(pp.pipelinePrompt(), pp.fileURL(), fileContent, s))
					.doOnNext(p -> {
						latestResponse = p.response();
					});
			
		});
	}
	
	

}

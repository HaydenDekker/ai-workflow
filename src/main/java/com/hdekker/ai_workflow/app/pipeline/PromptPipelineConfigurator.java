package com.hdekker.ai_workflow.app.pipeline;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hdekker.ai_workflow.files.FileHistory;
import com.hdekker.ai_workflow.files.PromptResponseFileSystemAdapter;
import com.hdekker.ai_workflow.llm.Prompter;
import com.hdekker.ai_workflow.pipeline.LLMAdapter;
import com.hdekker.ai_workflow.pipeline.LLMReducerAdapter;
import com.hdekker.ai_workflow.pipeline.PromptPipelineBuilder;
import com.hdekker.ai_workflow.pipeline.domain.PipelinePrompt;
import com.hdekker.ai_workflow.prompt.PromptRequest;
import com.hdekker.ai_workflow.prompt.PromptResponse;

import reactor.core.publisher.Flux;

public class PromptPipelineConfigurator {
	
	Logger log = LoggerFactory.getLogger(PromptPipelineConfigurator.class);
	
	final Flux<FileHistory> fileInputFlux;
	// TODO more a llm adapter.
	Prompter prompter;
	
	public PromptPipelineConfigurator(
			Flux<FileHistory> fileInputFlux,
			Prompter prompter
			){
		this.fileInputFlux = fileInputFlux;
		this.prompter = prompter;
	}

	public List<Flux<PromptResponse>> configure(List<PipelinePrompt> promptChain) {
		
		if(promptChain.size()==0) {
			log.warn("Empty prompt list, dev: consider adding validation to interface.");
			return List.of();
		}
		
		return promptChain.stream()
			.map(pp->{
				
				LLMAdapter gp = flux->flux.flatMap(fpe-> 
				prompter.call(pp.body() + "\n\r" + "```code" + fpe.file() + "\n\r" + "```" + "\n\r" + pp.outputStructure())
					.reduce((a,b)-> a+b)
					.map(s-> new PromptResponse(pp, fpe.fileURL(), fpe.file(), s)));
		
			
				LLMAdapter adapter = (pp.type()!=null && pp.type().equals("REDUCTION")) ? 
						new LLMReducerAdapter(prompter):
							gp;
				
				PromptPipelineBuilder.<PromptRequest, PromptResponse> instance()
						.withTrigger(fileInputFlux
								.map(fh-> new PromptRequest(pp, fh.currentFile().body(), fh.currentFile().url())))
						.prompting(adapter::call);
//						.persist(pr->{
//							promptResponseDatabase.save(pr);
//							PromptResponseFileSystemAdapter.createFile(pr, outputFolderPath);
//						})
//						.split(jsonItemListConverter)
//						.build();
				
				
				return Flux.just(
						new PromptResponse(pp, null, null, null));
			})
			.toList();
			
	}

}

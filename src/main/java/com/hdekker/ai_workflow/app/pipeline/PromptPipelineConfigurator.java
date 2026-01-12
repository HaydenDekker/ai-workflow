package com.hdekker.ai_workflow.app.pipeline;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hdekker.ai_workflow.files.FileHistory;
import com.hdekker.ai_workflow.files.PromptResponseFileSystemAdapter;
import com.hdekker.ai_workflow.llm.Prompter;
import com.hdekker.ai_workflow.pipeline.LLMAdapter;
import com.hdekker.ai_workflow.pipeline.LLMReducerAdapter;
import com.hdekker.ai_workflow.pipeline.SplittableStrategy;
import com.hdekker.ai_workflow.pipeline.domain.PipelinePrompt;
import com.hdekker.ai_workflow.prompt.PromptRequest;
import com.hdekker.ai_workflow.prompt.PromptResponse;

import reactor.core.publisher.Flux;

public class PromptPipelineConfigurator {
	
	Logger log = LoggerFactory.getLogger(PromptPipelineConfigurator.class);
	
	final Flux<FileHistory> fileInputFlux;
	// TODO more a llm adapter.
	Prompter prompter;
	Consumer<PromptResponse> persister;
	
	public PromptPipelineConfigurator(
			Flux<FileHistory> fileInputFlux,
			Prompter prompter,
			Consumer<PromptResponse> persister
			){
		this.fileInputFlux = fileInputFlux;
		this.prompter = prompter;
		this.persister = persister;
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
						new LLMReducerAdapter(prompter, pp):
							gp;
				
				return PromptPipelineBuilder.instance()
						.withDefinition(pp)
						.withTrigger(fileInputFlux
								.map(fh-> fh.to()))
						.prompting(adapter::call)
						.persist(persister)
						.split(SplittableStrategy.noSPLT())
						.build();
		
			})
			.toList();
			
	}

}

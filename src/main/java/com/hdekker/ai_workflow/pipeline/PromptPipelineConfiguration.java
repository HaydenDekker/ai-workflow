package com.hdekker.ai_workflow.pipeline;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hdekker.ai_workflow.database.promptresponse.PromptResponseDatabase;
import com.hdekker.ai_workflow.files.FileHistory;
import com.hdekker.ai_workflow.files.FileSystemRecursiveFileScannerAdapter;
import com.hdekker.ai_workflow.llm.GenericPromptCaller;
import com.hdekker.ai_workflow.llm.PromptResponseConverter;
import com.hdekker.ai_workflow.llm.output.LLMOutputParsingUtils;
import com.hdekker.ai_workflow.pipeline.domain.PipelinePrompt;
import com.hdekker.ai_workflow.pipeline.domain.PromptTriggerEvent;
import com.hdekker.ai_workflow.prompt.PromptConfiguration;

import reactor.core.publisher.Flux;

@Configuration
public class PromptPipelineConfiguration {
	
	Logger log = LoggerFactory.getLogger(PromptPipelineConfiguration.class);

	@Autowired
	FileSystemRecursiveFileScannerAdapter fileScanner;

	@Autowired
	GenericPromptCaller genericPromptCaller;
	
	@Autowired
	PromptResponseDatabase promptResponseDatabase;
	
	@Autowired
	PromptConfiguration promptConfiguration;
	
	ObjectMapper om = new ObjectMapper();
	
	PromptResponseConverter jsonItemListConverter = (s)->{
		String json = LLMOutputParsingUtils.extractJsonContent(s.response());
		List<Object> list = List.of();
		try {
			list = om.readValue(json, new TypeReference<List<Object>>() {});
		} catch (JsonProcessingException e) {
			log.error("Unexpected LLM response " + json);
			e.printStackTrace();
		}
		return list
			.stream()
			.map(resp->new PromptResponse(s.promptTitle(), resp.toString()))
			.toList();
	};
	
	public PromptPipelineConfiguration(
			FileSystemRecursiveFileScannerAdapter fileScanner,
			GenericPromptCaller genericPromptCaller,
			PromptConfiguration promptConfiguration,
			PromptResponseDatabase promptResponseDatabase) {
		
		this.fileScanner = fileScanner;
		this.genericPromptCaller = genericPromptCaller;
		this.promptConfiguration = promptConfiguration;
		this.promptResponseDatabase = promptResponseDatabase;
		
		List<PipelinePrompt> promptPipeline = promptConfiguration.getChain();
		Flux<PromptResponse> pipeline = build(promptPipeline);
		pipeline.subscribe(s-> log.info(s.toString()));
		
	}
	
	public Flux<PromptResponse> buildFileHistoryPipelineStage(PipelinePrompt pipelinePrompt){
		
		return PromptPipelineBuilder.<FileHistory, PromptResponse> instance()
			.withTrigger(fileScanner.flux())
			.prompting(f->{
				return f
				.flatMap(fh-> {
					return Flux.fromStream(
							genericPromptCaller.call(
									pipelinePrompt.title(), 
									pipelinePrompt.body() + " body: " +  fh.currentFile().body(), 
									pipelinePrompt.outputStructure(),
									jsonItemListConverter
									)
							.stream());
				});
			})
			.persist(promptResponseDatabase::save)
			.build();
		
	}
	
	Flux<PromptResponse> buildPromptResponsePipelineStage(Flux<PromptResponse> fs, PipelinePrompt pipelinePrompt){
		
		PromptResponseConverter prc = (r) -> List.of(r);
		
		return PromptPipelineBuilder.<PromptResponse, PromptResponse> instance()
			.withTrigger(fs)
			.prompting(flux->{
				return flux.flatMap(fpe-> 
					Flux.fromStream(
						genericPromptCaller.call(
							pipelinePrompt.title(), 
							pipelinePrompt.body() + " body: " +  fpe.response(), 
							pipelinePrompt.outputStructure(),
							prc
							).stream())
						);	
			})
			.persist(promptResponseDatabase::save)
			.build();
			
	}
	
	Map<String, Flux<PromptResponse>> promptTitleMap = new HashMap<String, Flux<PromptResponse>>();
	
	
	public Flux<PromptResponse> build(List<PipelinePrompt> promptPipeline){
		
		List<PipelinePrompt> responsePrompts = new ArrayList<PipelinePrompt>();
		
		promptPipeline.stream()
			.forEach(f-> {
				if(f.event().equals(PromptTriggerEvent.FILE_SYS_HASH_CHANGED_EVENT.name())) {
					
					Flux<PromptResponse> pr = buildFileHistoryPipelineStage(f);
					promptTitleMap.put(PromptTriggerEvent.PROMPT_RESPONSE_EVENT.name() + "_" + f.title(), pr);
				
				}else {
					responsePrompts.add(f);
				}
			});
		
		responsePrompts.forEach(pp->{
			Flux<PromptResponse> fs = promptTitleMap.get(pp.event());
			Flux<PromptResponse> fs2 = buildPromptResponsePipelineStage(fs, pp);
			promptTitleMap.put(PromptTriggerEvent.PROMPT_RESPONSE_EVENT.name() + "_" + pp.title(), fs2);
		});
		
		if(responsePrompts.size()==0) {
			log.warn("User input empty list, consider adding validation to interface.");
			return Flux.empty();
		}
		
		PipelinePrompt subscribePrompt = responsePrompts.get(responsePrompts.size()-1);
	
		Flux<PromptResponse> fs = promptTitleMap.get(PromptTriggerEvent.PROMPT_RESPONSE_EVENT.name() + "_" + subscribePrompt.title());

		return fs;
		
	}

}

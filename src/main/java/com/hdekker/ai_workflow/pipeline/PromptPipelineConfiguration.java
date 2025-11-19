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
import com.hdekker.ai_workflow.llm.output.LLMOutputParsingUtils;
import com.hdekker.ai_workflow.pipeline.domain.PipelinePrompt;
import com.hdekker.ai_workflow.pipeline.domain.PromptTriggerEvent;
import com.hdekker.ai_workflow.prompt.PromptConfiguration;
import com.hdekker.ai_workflow.prompt.SystemPromptConfiguration;

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
	
	SplittableStrategy<PromptResponse, PromptResponse> jsonItemListConverter = (s)->{
		String json = LLMOutputParsingUtils.extractJsonContent(s.response());
		List<Object> list = List.of();
		try {
			list = om.readValue(json, new TypeReference<List<Object>>() {});
		} catch (JsonProcessingException e) {
			log.error("Unexpected LLM response " + s.response());
			e.printStackTrace();
		}
		return list
			.stream()
			.map(resp->new PromptResponse(s.prompt(), s.fileName(), s.file(), resp.toString()))
			.toList();
	};
	
	@Autowired
	SystemPromptConfiguration systemPromptConfiguration;
	
	public PromptPipelineConfiguration(
			FileSystemRecursiveFileScannerAdapter fileScanner,
			GenericPromptCaller genericPromptCaller,
			PromptConfiguration promptConfiguration,
			PromptResponseDatabase promptResponseDatabase,
			SystemPromptConfiguration systemPromptConfiguration) {
		
		this.fileScanner = fileScanner;
		this.genericPromptCaller = genericPromptCaller;
		this.promptConfiguration = promptConfiguration;
		this.promptResponseDatabase = promptResponseDatabase;
		this.systemPromptConfiguration = systemPromptConfiguration;
		
//		List<PipelinePrompt> promptPipeline = promptConfiguration.getChain();
//		Flux<PromptResponse> pipeline = build(promptPipeline);
//		pipeline.subscribe(s-> log.info(s.toString()));
		
		systemPromptConfiguration.getPromptChains()
			.stream()
			.peek(pc-> log.info("Configuring " + pc.chain().get(0).title()))
			.map(pc-> build(pc.chain()))
			.forEach(flux-> {
				
				log.info("starting");
				flux.subscribe(s-> log.info(s.toString()));
			});
		
	}
	
	public Flux<PromptResponse> buildFileHistoryPipelineStage(PipelinePrompt pipelinePrompt){
		
		return PromptPipelineBuilder.<FileHistory, PromptResponse> instance()
			.withTrigger(fileScanner.flux())
			.prompting(f->
				f.map(fh-> 
					genericPromptCaller.call(
							pipelinePrompt,
							fh.currentFile().body(),
							fh.currentFile().url()
						)
				)
			)
			.persist(promptResponseDatabase::save)
			.split(jsonItemListConverter)
			.build();
		
	}
	
	Flux<PromptResponse> buildPromptResponsePipelineStage(Flux<PromptResponse> fs, PipelinePrompt pipelinePrompt){
		
		// TODO Low priority - can remove type argument, as output is now always a prompt response.
		SplittableStrategy<PromptResponse, PromptResponse> prc = (r) -> List.of(r);
		
		return PromptPipelineBuilder.<PromptResponse, PromptResponse> instance()
			.withTrigger(fs)
			.enrichFirst(pr-> new PromptResponse(pr.prompt(), pr.fileName(), pr.file(), pr.prompt().title() + "\n\r\n\r" +  pr.prompt().body() + " Response: \n\r\n\r" + pr.response()))
			.prompting(flux->
				flux.map(fpe-> 
					genericPromptCaller.call(
						pipelinePrompt, 
						fpe.response(),
						fpe.fileName())
					)
			)
			.persist(promptResponseDatabase::save)
			.split(prc)
			.build();
			
	}
	
	
	public Flux<PromptResponse> build(List<PipelinePrompt> promptPipeline){
		
		if(promptPipeline.size()==0) {
			log.warn("Empty prompt list, dev: consider adding validation to interface.");
			return Flux.empty();
		}
		
		Map<String, Flux<PromptResponse>> promptTitleMap = new HashMap<String, Flux<PromptResponse>>();
		
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
		
		PipelinePrompt subscribePrompt = promptPipeline.get(promptPipeline.size()-1);
	
		Flux<PromptResponse> fs = promptTitleMap.get(PromptTriggerEvent.PROMPT_RESPONSE_EVENT.name() + "_" + subscribePrompt.title())
				.doOnNext(pr -> log.info(subscribePrompt.title()));

		return fs;
		
	}

}

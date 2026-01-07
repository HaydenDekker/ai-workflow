package com.hdekker.ai_workflow.pipeline;

import java.io.IOException;
import java.nio.file.Path;
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
import com.hdekker.ai_workflow.files.FileSystemRecursiveFileScannerAdapter;
import com.hdekker.ai_workflow.files.FileSystemScannerConfig;
import com.hdekker.ai_workflow.files.PromptResponseFileSystemAdapter;
import com.hdekker.ai_workflow.llm.GenericPromptCaller;
import com.hdekker.ai_workflow.llm.output.LLMOutputParsingUtils;
import com.hdekker.ai_workflow.pipeline.domain.PipelinePrompt;
import com.hdekker.ai_workflow.pipeline.domain.PromptTriggerEvent;
import com.hdekker.ai_workflow.prompt.PromptConfiguration;
import com.hdekker.ai_workflow.prompt.PromptRequest;
import com.hdekker.ai_workflow.prompt.PromptResponse;
import com.hdekker.ai_workflow.prompt.SystemPromptConfiguration;

import reactor.core.publisher.Flux;

/**
 *  To build the configured pipelines ready for file processing.
 * 
 */
@Configuration
public class PromptPipelineConfiguration {
	
	Logger log = LoggerFactory.getLogger(PromptPipelineConfiguration.class);
	
	@Autowired
	FileSystemScannerConfig fileScannerConfig;

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
			.map(resp->new PromptResponse(s.prompt(), s.fileName(), s.fileContents(), resp.toString()))
			.toList();
	};
	
	@Autowired
	SystemPromptConfiguration systemPromptConfiguration;
	
	// TODO component and pass in.
	Path outputFolderPath;
	
	public PromptPipelineConfiguration(
			FileSystemRecursiveFileScannerAdapter fileScanner,
			GenericPromptCaller genericPromptCaller,
			PromptConfiguration promptConfiguration,
			PromptResponseDatabase promptResponseDatabase,
			SystemPromptConfiguration systemPromptConfiguration,
			FileSystemScannerConfig fileScannerConfig) {
		
		this.fileScanner = fileScanner;
		this.genericPromptCaller = genericPromptCaller;
		this.promptConfiguration = promptConfiguration;
		this.promptResponseDatabase = promptResponseDatabase;
		this.systemPromptConfiguration = systemPromptConfiguration;
		this.fileScannerConfig = fileScannerConfig;
		
		
		try {
			outputFolderPath = fileScannerConfig.getUrl().getFile().toPath();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	
		systemPromptConfiguration.getPromptChains()
			.stream()
			.peek(pc-> log.info("Configuring " + pc.chain().get(0).title()))
			.map(pc-> build(pc.chain()))
			.forEach(flux-> {
				
				log.info("starting");
				flux.subscribe();
			});
		
	}
	
	PromptRequest convert(PipelinePrompt pipelinePrompt, PromptResponse response) {
		
		return new PromptRequest(
				pipelinePrompt, 
				response.prompt().title() + "\n\r\n\r" +  response.prompt().body() + " Response: \n\r\n\r" + response.response(), 
				response.fileName());
		
	}
	
	Flux<PromptResponse> buildPromptPipelineStage(
			Flux<PromptRequest> fs, 
			PipelinePrompt pipelinePrompt,
			SplittableStrategy<PromptResponse, PromptResponse> prc,
			LLMAdapter adapter ){
		
		return PromptPipelineBuilder.<PromptRequest, PromptResponse> instance()
			.withTrigger(fs)
			.prompting(adapter::call)
			.persist(pr->{
				promptResponseDatabase.save(pr);
				PromptResponseFileSystemAdapter.createFile(pr, outputFolderPath);
			})
			.split(prc)
			.build();
			
	}
	
	
	private Flux<PromptResponse> build(List<PipelinePrompt> promptPipeline){
		
		if(promptPipeline.size()==0) {
			log.warn("Empty prompt list, dev: consider adding validation to interface.");
			return Flux.empty();
		}
		
		Map<String, Flux<PromptResponse>> promptTitleMap = new HashMap<String, Flux<PromptResponse>>();
		
		List<PipelinePrompt> responsePrompts = new ArrayList<PipelinePrompt>();
		
		promptPipeline.stream()
			.forEach(pp-> {
				if(pp.event().equals(PromptTriggerEvent.FILE_SYS_HASH_CHANGED_EVENT.name())) {
					
					LLMAdapter gp = flux->flux.map(fpe-> 
					genericPromptCaller.call(
						pp,
						fpe.file(),
						fpe.fileURL()));
				
					LLMAdapter adapter = (pp.type()!=null && pp.type().equals("REDUCTION")) ? 
							new LLMReducerAdapter(genericPromptCaller):
								gp;
					
					Flux<PromptResponse> pr = buildPromptPipelineStage(
							fileScanner.flux()
								.map(fh-> new PromptRequest(pp, fh.currentFile().body(), fh.currentFile().url())),
							pp, 
							jsonItemListConverter,
							adapter
						);
					promptTitleMap.put(PromptTriggerEvent.PROMPT_RESPONSE_EVENT.name() + "_" + pp.title(), pr);
				
				}else {
					responsePrompts.add(pp);
				}
			});
		
		responsePrompts.forEach(pp->{
			Flux<PromptResponse> fs = promptTitleMap.get(pp.event());
			
			LLMAdapter gp = flux->flux.map(fpe-> 
				genericPromptCaller.call(
					pp, 
					fpe.file(),
					fpe.fileURL()));
			
			LLMAdapter adapter = (pp.type()!=null && pp.type().equals("REDUCTION")) ? 
					new LLMReducerAdapter(genericPromptCaller):
						gp;
			
			Flux<PromptResponse> fs2 = buildPromptPipelineStage(
					fs.map(presp-> convert(pp, presp)), 
					pp, 
					SplittableStrategy.noSPLT(),
					adapter
					);
			promptTitleMap.put(PromptTriggerEvent.PROMPT_RESPONSE_EVENT.name() + "_" + pp.title(), fs2);
		});
		
		PipelinePrompt subscribePrompt = promptPipeline.get(promptPipeline.size()-1);
	
		Flux<PromptResponse> fs = promptTitleMap.get(PromptTriggerEvent.PROMPT_RESPONSE_EVENT.name() + "_" + subscribePrompt.title())
				.doOnNext(pr -> log.info(subscribePrompt.title()));

		return fs;
		
	}

}

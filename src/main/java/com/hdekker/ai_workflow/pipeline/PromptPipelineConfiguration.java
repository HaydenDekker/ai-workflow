package com.hdekker.ai_workflow.pipeline;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hdekker.ai_workflow.database.filemetadata.FileMetadataDatabase;
import com.hdekker.ai_workflow.database.promptresponse.PromptResponseDatabase;
import com.hdekker.ai_workflow.files.FileHistory;
import com.hdekker.ai_workflow.files.FileSystemRecursiveFileScannerAdapter;
import com.hdekker.ai_workflow.llm.GenericPromptCaller;
import com.hdekker.ai_workflow.llm.PromptResponseConverter;
import com.hdekker.ai_workflow.llm.output.LLMOutputParsingUtils;
import com.hdekker.ai_workflow.pipeline.domain.PipelinePrompt;
import com.hdekker.ai_workflow.prompt.PromptConfiguration;

import reactor.core.publisher.Flux;

@Configuration
public class PromptPipelineConfiguration {
	
	Logger log = LoggerFactory.getLogger(PromptPipelineConfiguration.class);

	@Autowired
	FileSystemRecursiveFileScannerAdapter fileScanner;
	
	@Autowired
	FileMetadataDatabase fileMetadataDatabase;
	
	@Autowired
	GenericPromptCaller genericPromptCaller;
	
	@Autowired
	PromptResponseDatabase promptResponseDatabase;
	
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
			GenericPromptCaller genericPromptCaller) {
		
		this.fileScanner = fileScanner;
		this.genericPromptCaller = genericPromptCaller;

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
								PromptConfiguration.SOLID_COMPLAINCE_PROMPT + " body: " +  fh.currentFile().body(), 
								PromptConfiguration.PRIORITY_ORDER_PROMPT_OUTPUT,
								jsonItemListConverter
								)
						.stream());
			});
		})
		.persist(s->{
			promptResponseDatabase.save(new PromptResponse(PromptConfiguration.SOLID_COMPLIANCE_PROMPT_TITLE, s.toString()));
		})
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
								PromptConfiguration.PRIORITY_ORDER_PROMPT + " body: " +  fpe.response(), 
								PromptConfiguration.PRIORITY_ORDER_PROMPT_OUTPUT,
								prc
								).stream())
							);	
				})
				.persist(pr-> {
					promptResponseDatabase.save(pr);
				})
				.build();
			
	}
	
	
	public Flux<PromptResponse> build(List<PipelinePrompt> promptPipeline){
		
//		promptPipeline.stream()
//			.map(f-> )
	
		PipelinePrompt solidFileSystemEventPipelinePrompt = new PipelinePrompt(PromptConfiguration.SOLID_COMPLIANCE_PROMPT_TITLE);
		
		Flux<PromptResponse> fs = buildFileHistoryPipelineStage(solidFileSystemEventPipelinePrompt);
		
		PipelinePrompt priorityClassificationPipelineStage = new PipelinePrompt(PromptConfiguration.PRIORITY_ORDER_PROMPT_TITLE);
		
		Flux<PromptResponse> fs2 = buildPromptResponsePipelineStage(fs, priorityClassificationPipelineStage);
		return fs2;
		
	}

}

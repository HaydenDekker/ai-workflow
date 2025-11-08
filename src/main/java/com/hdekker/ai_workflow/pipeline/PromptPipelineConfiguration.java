package com.hdekker.ai_workflow.pipeline;

import java.time.Duration;
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
import com.hdekker.ai_workflow.files.FileComparator;
import com.hdekker.ai_workflow.files.FileSystemRecursiveFileScannerAdapter;
import com.hdekker.ai_workflow.files.domain.FileMetadata;
import com.hdekker.ai_workflow.llm.GenericPromptCaller;
import com.hdekker.ai_workflow.llm.PromptResponseConverter;
import com.hdekker.ai_workflow.llm.output.LLMOutputParsingUtils;
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
	
	// should trigger update
	record FilePromptEvent2(FileMetadata event, PromptResponse result) {}
	
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
			FileMetadataDatabase fileMetadataDatabase,
			GenericPromptCaller genericPromptCaller) {
		
		this.fileScanner = fileScanner;
		this.fileMetadataDatabase = fileMetadataDatabase;
		this.genericPromptCaller = genericPromptCaller;
		
		//fs2.subscribe(s-> log.info(s.toString()));
		
	}
	
	public Flux<PromptResponse> build(){
		
		FileComparator fileComparator = new FileComparator(fileMetadataDatabase);
		
		Flux<FilePromptEvent2> fs = PromptPipelineBuilder.<FileMetadata, FilePromptEvent2> instance()
				.withTrigger(fileScanner.flux())
				.prompting(f->{
					return f
					.map(fileComparator::matches)
					.filter(fh->!fh.hashMatches())
					.doOnNext(fh->fileMetadataDatabase.save(fh.currentFile()))
					.flatMap(fh-> {
						return Flux.fromStream(
								//solidPromptCaller.prompt(fh.currentFile().body(), PromptConfiguration.SOLID_COMPLIANCE_PROMPT_OUTPUT).stream())
								genericPromptCaller.call(
										PromptConfiguration.SOLID_COMPLIANCE_PROMPT_TITLE, 
										PromptConfiguration.SOLID_COMPLAINCE_PROMPT + " body: " +  fh.currentFile().body(), 
										PromptConfiguration.PRIORITY_ORDER_PROMPT_OUTPUT,
										jsonItemListConverter
										)
								.stream()
								.map(sc-> {
									return new FilePromptEvent2(fh.currentFile(), sc);
								}));
					});
							//.doOnNext(fpe-> log.info(fpe.toString()));
				})
				.persist(s->{
					promptResponseDatabase.save(new PromptResponse(PromptConfiguration.SOLID_COMPLIANCE_PROMPT_TITLE, s.toString()));
				})
				.build();
			
			PromptResponseConverter prc = (r) -> List.of(r);
			
			Flux<PromptResponse> fs2  = PromptPipelineBuilder.<FilePromptEvent2, PromptResponse> instance()
				.withTrigger(fs.window(Duration.ofSeconds(5)).flatMap(f->f))
				.prompting(flux->{
					return flux.flatMap(fpe-> 
						Flux.fromStream(
							genericPromptCaller.call(
								PromptConfiguration.PRIORITY_ORDER_PROMPT_TITLE, 
								fpe.result.toString() + " " + PromptConfiguration.PRIORITY_ORDER_PROMPT + " body: " +  fpe.event.body(), 
								PromptConfiguration.PRIORITY_ORDER_PROMPT_OUTPUT,
								prc
								).stream())
							);	
				})
				.persist(pr-> {
					promptResponseDatabase.save(pr);
				})
				.build();
			
			return fs2;
		
	}

}

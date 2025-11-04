package com.hdekker.ai_workflow.pipeline;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

import com.hdekker.ai_workflow.database.filemetadata.FileMetadataDatabase;
import com.hdekker.ai_workflow.database.promptresponse.PromptResponseDatabase;
import com.hdekker.ai_workflow.database.solid.SolidComplianceDatabase;
import com.hdekker.ai_workflow.files.FileComparator;
import com.hdekker.ai_workflow.files.FileSystemRecursiveFileScannerAdapter;
import com.hdekker.ai_workflow.files.domain.FileMetadata;
import com.hdekker.ai_workflow.llm.GenericPromptCaller;
import com.hdekker.ai_workflow.llm.PromptConfiguration;
import com.hdekker.ai_workflow.llm.SOLIDPromtCaller;
import com.hdekker.ai_workflow.llm.output.SOLIDCompliance;

import reactor.core.publisher.Flux;

@Configuration
public class PromptPipelineConfiguration {
	
	Logger log = LoggerFactory.getLogger(PromptPipelineConfiguration.class);

	@Autowired
	FileSystemRecursiveFileScannerAdapter fileScanner;
	
	@Autowired
	FileMetadataDatabase fileMetadataDatabase;
	
	@Autowired
	SOLIDPromtCaller solidPromptCaller;
	
	@Autowired
	SolidComplianceDatabase solidComplianceDatabase;
	
	@Autowired
	GenericPromptCaller genericPromptCaller;
	
	@Autowired
	PromptResponseDatabase promptResponseDatabase;
	
	// should trigger update
	record FilePromptEvent(FileMetadata event, SOLIDCompliance result) {}
	
	public PromptPipelineConfiguration(
			FileSystemRecursiveFileScannerAdapter fileScanner,
			FileMetadataDatabase fileMetadataDatabase,
			SOLIDPromtCaller solidPromptCaller,
			GenericPromptCaller genericPromptCaller) {
		
		FileComparator fileComparator = new FileComparator(fileMetadataDatabase);
		
		this.fileScanner = fileScanner;
		this.fileMetadataDatabase = fileMetadataDatabase;
		this.solidPromptCaller = solidPromptCaller;
		this.genericPromptCaller = genericPromptCaller;
		
		Flux<FilePromptEvent> fs = PromptPipelineBuilder.<FileMetadata, FilePromptEvent> instance()
			.withTrigger(fileScanner.flux())
			.prompting(f->{
				return f
				.map(fileComparator::matches)
				.filter(fh->!fh.hashMatches())
				.doOnNext(fh->fileMetadataDatabase.save(fh.currentFile()))
				.flatMap(fh-> {
					return Flux.fromStream(solidPromptCaller.prompt(fh.currentFile().body()).stream())
						.map(sc-> {
							return new FilePromptEvent(fh.currentFile(), sc);
						});
				})
				.doOnNext(fpe-> log.info(fpe.toString()));
			})
			.persist(s->{
				solidComplianceDatabase.save(s.result(), s.event.hash());
			})
			.build();
		
		//fs.subscribe(s-> log.info(s.toString()));
		
		Flux<PromptResponse> fs2  = PromptPipelineBuilder.<FilePromptEvent, PromptResponse> instance()
			.withTrigger(fs.window(Duration.ofSeconds(5)).flatMap(f->f))
			.prompting(flux->{
				return flux.map(fpe-> genericPromptCaller.call(PromptConfiguration.PRIORITY_ORDER_PROMPT + " body: " +  fpe.event.body(), fpe.result.toString()));	
			})
			.persist(pr-> {
				promptResponseDatabase.save(pr);
			})
			.build();
		
		fs2.subscribe(s-> log.info(s.toString()));
		
	}

}

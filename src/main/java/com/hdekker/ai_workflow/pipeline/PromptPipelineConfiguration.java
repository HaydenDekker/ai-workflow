package com.hdekker.ai_workflow.pipeline;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

import com.hdekker.ai_workflow.database.filemetadata.FileMetadataDatabase;
import com.hdekker.ai_workflow.database.solid.SolidComplianceDatabase;
import com.hdekker.ai_workflow.files.FileComparator;
import com.hdekker.ai_workflow.files.FileSystemRecursiveFileScannerAdapter;
import com.hdekker.ai_workflow.files.domain.FileMetadata;
import com.hdekker.ai_workflow.llm.SOLIDPromtCaller;
import com.hdekker.ai_workflow.llm.output.SOLIDCompliance;

import reactor.core.publisher.Flux;
import reactor.util.function.Tuples;

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
	
	record FilePromptEvent(FileMetadata event, SOLIDCompliance result) {}
	
	public PromptPipelineConfiguration(
			FileSystemRecursiveFileScannerAdapter fileScanner,
			FileMetadataDatabase fileMetadataDatabase,
			SOLIDPromtCaller solidPromptCaller) {
		
		FileComparator fileComparator = new FileComparator(fileMetadataDatabase);
		
		this.fileScanner = fileScanner;
		this.fileMetadataDatabase = fileMetadataDatabase;
		this.solidPromptCaller = solidPromptCaller;
		
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
				});
			})
			.persist(s->{
				solidComplianceDatabase.save(s.result(), s.event.hash());
			})
			.build();
		
		fs.subscribe(s-> log.info(s.toString()));
		
	}

}

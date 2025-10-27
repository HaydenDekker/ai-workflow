package com.hdekker.ai_workflow.pipeline;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

import com.hdekker.ai_workflow.database.filemetadata.FileMetadataDatabase;
import com.hdekker.ai_workflow.database.solid.SolidComplianceDatabase;
import com.hdekker.ai_workflow.files.FileComparator;
import com.hdekker.ai_workflow.files.FileHash;
import com.hdekker.ai_workflow.files.FileSystemRecursiveFileScannerAdapter;
import com.hdekker.ai_workflow.files.domain.FileMetadata;
import com.hdekker.ai_workflow.llm.SOLIDPromtCaller;

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
	
	public PromptPipelineConfiguration(
			FileSystemRecursiveFileScannerAdapter fileScanner,
			FileMetadataDatabase fileMetadataDatabase,
			SOLIDPromtCaller solidPromptCaller) {
		
		FileComparator fileComparator = new FileComparator(fileMetadataDatabase);
		
		this.fileScanner = fileScanner;
		this.fileMetadataDatabase = fileMetadataDatabase;
		this.solidPromptCaller = solidPromptCaller;
		
		Flux<String> fs = PromptPipelineBuilder.<FileMetadata, String> instance()
			.withTrigger(fileScanner.flux())
			.prompting(f->{
				return f
				.map(fileComparator::matches)
				.filter(fh->!fh.hashMatches())
				.doOnNext(fh->fileMetadataDatabase.save(fh.currentFile()))
				.map(fh-> {
					return Tuples.of(fh.currentFile().hash(), solidPromptCaller.prompt(fh.currentFile().body()));
				})
				.doOnNext(s-> s.getT2().forEach(sc->solidComplianceDatabase.save(sc, s.getT1())))
				.map(s->s.getT2().toString());
			})
			.build();
		
		fs.subscribe(s-> log.info(s));
		
	}

}

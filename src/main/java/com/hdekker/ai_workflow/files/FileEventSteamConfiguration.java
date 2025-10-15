package com.hdekker.ai_workflow.files;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

import com.hdekker.ai_workflow.database.FileMetadataDatabase;
import com.hdekker.ai_workflow.files.domain.FileMetadata;
import com.hdekker.ai_workflow.llm.SOLIDPromtCaller;

@Configuration
public class FileEventSteamConfiguration {
	
	Logger log = LoggerFactory.getLogger(FileEventSteamConfiguration.class);

	@Autowired
	FileSystemRecursiveFileScannerAdapter fileScanner;
	
	@Autowired
	FileMetadataDatabase fileMetadataDatabase;
	
	@Autowired
	SOLIDPromtCaller solidPromptCaller;
	
	public FileEventSteamConfiguration(
			FileSystemRecursiveFileScannerAdapter fileScanner,
			FileMetadataDatabase fileMetadataDatabase,
			SOLIDPromtCaller solidPromptCaller) {
		
		log.info("subscribing");
		
		FileComparator fileComparator = new FileComparator(fileMetadataDatabase);
		FileHash fileHash = new FileHash();
		
		this.fileScanner = fileScanner;
		this.fileMetadataDatabase = fileMetadataDatabase;
		this.solidPromptCaller = solidPromptCaller;
		
		fileScanner.flux()
			.map(m-> {
				String s = m.getPayload();
				String hash = fileHash.hash(s);
				String file = (String) m.getHeaders().get("file_relativePath");
				return new FileMetadata(file, s, hash);
			})
			.map(fileComparator::matches)
			.filter(fh->!fh.hashMatches())
			.map(fh-> {
				log.info("calling llm.");
				return solidPromptCaller.prompt(fh.currentFile().body());
			})
			.subscribe(s->{
			
				log.info(s.toString());
			
		});
		
	}

}

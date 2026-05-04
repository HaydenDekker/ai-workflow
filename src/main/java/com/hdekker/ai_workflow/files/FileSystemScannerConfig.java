package com.hdekker.ai_workflow.files;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

@Configuration
@ConfigurationProperties("scanner")
public class FileSystemScannerConfig {
	
	Logger log = LoggerFactory.getLogger(FileSystemScannerConfig.class);
	
	Resource url;

	public Resource getUrl() {
		return url;
	}

	public void setUrl(Resource url) {
		this.url = url;
	}
	
	@PostConstruct
	public void log() {
		log.info(url.getFilename());
		
	}

}

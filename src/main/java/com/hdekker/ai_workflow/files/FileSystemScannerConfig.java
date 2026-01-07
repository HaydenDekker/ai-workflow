package com.hdekker.ai_workflow.files;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import jakarta.annotation.PostConstruct;

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
	
	String outputFolder;
	
	public String getOutputFolder() {
		return outputFolder;
	}

	public void setOutputFolder(String outputFolder) {
		this.outputFolder = outputFolder;
	}

	@PostConstruct
	public void log() {
		log.info(url.getFilename());
		log.info(outputFolder);
	}

}

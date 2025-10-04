package com.hdekker.ai_workflow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

@Configuration
@ConfigurationProperties("scanner")
public class FileSystemScannerConfig {
	
	Logger log = LoggerFactory.getLogger(FileSystemScannerConfig.class);
	
	String url;

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}
	
	@PostConstruct
	public void log() {
		log.info(url);
	}

}

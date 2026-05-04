package com.hdekker.ai_workflow.prompt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration()
@ConfigurationProperties("prompt-config")
public class PromptConfiguration {
	
	Logger log = LoggerFactory.getLogger(PromptConfiguration.class);
	
	String predefinedPromptFilePath;
	
	public String getPredefinedPromptFilePath() {
		return predefinedPromptFilePath;
	}

	public void setPredefinedPromptFilePath(String predefinedPromptFilePath) {
		this.predefinedPromptFilePath = predefinedPromptFilePath;
	}

	@PostConstruct
	public void log() {
		log.info("Pre-defined prompt/s, file path:" + predefinedPromptFilePath);
	}

}

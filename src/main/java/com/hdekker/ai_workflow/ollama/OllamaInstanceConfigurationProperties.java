package com.hdekker.ai_workflow.ollama;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

@Configuration
@ConfigurationProperties(value = "app.ai")
public class OllamaInstanceConfigurationProperties {
	
	Logger log = LoggerFactory.getLogger(OllamaInstanceConfigurationProperties.class);
	
	String endpoint;
	String model;
	public String getEndpoint() {
		return endpoint;
	}
	public void setEndpoint(String endpoint) {
		this.endpoint = endpoint;
	}
	public String getModel() {
		return model;
	}
	public void setModel(String model) {
		this.model = model;
	}
	
	@PostConstruct
	public void log() {
		log.info("endpoint: " + endpoint + " model:" + model);
	}

}

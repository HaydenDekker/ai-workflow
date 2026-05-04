package com.hdekker.ai_workflow.adapter.outbound.llm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(value = "app.ai")
public class OpenAiInstanceConfigurationProperties {
	
	private static final Logger log = LoggerFactory.getLogger(OpenAiInstanceConfigurationProperties.class);
	
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

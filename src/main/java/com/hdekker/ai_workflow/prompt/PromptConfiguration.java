package com.hdekker.ai_workflow.prompt;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import com.hdekker.ai_workflow.pipeline.domain.PipelinePrompt;

import jakarta.annotation.PostConstruct;

@Configuration()
@ConfigurationProperties("prompt-config")
public class PromptConfiguration {
	
	Logger log = LoggerFactory.getLogger(PromptConfiguration.class);
	
	List<PipelinePrompt> chain;
	
	public List<PipelinePrompt> getChain() {
		return chain;
	}

	public void setChain(List<PipelinePrompt> chain) {
		this.chain = chain;
	}
	
	@PostConstruct
	public void log() {
		chain.forEach(pp->log.info(pp.toString()));
	}

}

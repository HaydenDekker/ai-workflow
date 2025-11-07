package com.hdekker.ai_workflow.ollama;

import java.util.List;

import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OllamaInstanceConfiguration {

	@Autowired
	OllamaInstanceConfigurationProperties ollamaInstanceConfigurationProperties;
	
	@Bean
	public OllamaChatModel ollamaChatModel() {
		OllamaApi api = OllamaInstanceAdapterUtils.createAPI(ollamaInstanceConfigurationProperties.endpoint);
		List<OllamaChatModel> models = OllamaInstanceAdapterUtils.getModel(api);
		return models.stream()
				.filter(m-> m.getDefaultOptions().getModel().equals(ollamaInstanceConfigurationProperties.model))
				.findFirst()
				.orElseThrow();
	}
	
}

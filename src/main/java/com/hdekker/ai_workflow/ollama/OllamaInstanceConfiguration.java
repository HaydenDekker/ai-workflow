package com.hdekker.ai_workflow.ollama;

import java.util.List;

import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OllamaInstanceConfiguration {

	public static final String TEST_ENDPOINT_OLLAMA = "http://192.168.2.108:11434";
	public static final String TARGET_MODEL = "gemma3:27b"; 
	
	@Bean
	public OllamaChatModel ollamaChatModel() {
		OllamaApi api = OllamaInstanceAdapterUtils.createAPI(TEST_ENDPOINT_OLLAMA);
		List<OllamaChatModel> models = OllamaInstanceAdapterUtils.getModel(api);
		return models.get(0);
	}
	
}

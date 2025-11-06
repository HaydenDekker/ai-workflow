package com.hdekker.ai_workflow.ollama;

import java.util.List;

import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OllamaInstanceConfiguration {

	public static final String LOCAL_TEST_ENDPOINT = "http://127.0.0.1:11434";
	public static final String TEST_ENDPOINT_OLLAMA = "http://192.168.2.108:11434";
	public static final String TARGET_MODEL = "gemma3:27b"; 
	public static final String TARGET_MODEL_LOCAL = "gemma3:4b";
	
	public static final String ACTIVE_ENDPOINT = TEST_ENDPOINT_OLLAMA;
	public static final String ACTIVE_MODEL = TARGET_MODEL;
	
	@Bean
	public OllamaChatModel ollamaChatModel() {
		OllamaApi api = OllamaInstanceAdapterUtils.createAPI(ACTIVE_ENDPOINT);
		List<OllamaChatModel> models = OllamaInstanceAdapterUtils.getModel(api);
		return models.stream()
				.filter(m-> m.getDefaultOptions().getModel().equals(ACTIVE_MODEL))
				.findFirst()
				.orElseThrow();
	}
	
}

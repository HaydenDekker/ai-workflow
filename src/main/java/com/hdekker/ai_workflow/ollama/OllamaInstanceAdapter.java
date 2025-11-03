package com.hdekker.ai_workflow.ollama;

import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.stereotype.Service;

@Service
public class OllamaInstanceAdapter {

	public OllamaApi createAPI(String endpoint) {
		
		return OllamaApi.builder()
					.baseUrl(endpoint)
					.build();
	}
	
}

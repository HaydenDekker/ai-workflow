package com.hdekker.ai_workflow.ollama;

import java.util.List;

import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaApi.Model;
import org.springframework.ai.ollama.api.OllamaOptions;

public class OllamaInstanceAdapterUtils {

	public static OllamaApi createAPI(String endpoint) {
		
		return OllamaApi.builder()
					.baseUrl(endpoint)
					.build();
	}
	
	public static List<OllamaChatModel> getModel(OllamaApi api) {
		List<Model> models = api.listModels().models();
		
		return models.stream()
				.map(mod->{
					OllamaOptions options = OllamaOptions.builder().model(mod.model())
							.build();
					return OllamaChatModel.builder()
								.ollamaApi(api)
								.defaultOptions(options)
								.build();
				})
				.toList();
	}
	
	
}

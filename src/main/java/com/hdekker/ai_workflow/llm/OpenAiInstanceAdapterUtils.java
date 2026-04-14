package com.hdekker.ai_workflow.llm;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaApi.Model;
import org.springframework.ai.ollama.api.OllamaOptions;

public class OpenAiInstanceAdapterUtils {

	private static final Logger log = LoggerFactory.getLogger(OpenAiInstanceAdapterUtils.class);

	static {
		if (log.isInfoEnabled()) {
			log.info("OpenAiInstanceAdapterUtils: Optional utility methods for API creation and model discovery. Not required for basic operation.");
		}
	}

	public static OllamaApi createApi(String baseUrl) {
		
		return OllamaApi.builder()
					.baseUrl(baseUrl)
					.build();
	}
	
	public static List<OllamaChatModel> getModels(OllamaApi api) {
		List<Model> models = api.listModels().models();
		
		if (log.isInfoEnabled()) {
			log.info("Found {} models from OpenAI-compatible endpoint", models.size());
		}
		
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

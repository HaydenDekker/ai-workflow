package com.hdekker.ai_workflow.adapter.outbound.llm;

import java.util.List;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.openai.api.OpenAiApi;

public class OpenAiInstanceAdapterUtils {

	private static final Logger log = LoggerFactory.getLogger(OpenAiInstanceAdapterUtils.class);

	static {
		if (log.isInfoEnabled()) {
			log.info("OpenAiInstanceAdapterUtils: Optional utility methods for API creation and model discovery. Not required for basic operation.");
		}
	}

	public static OpenAiApi createApi(String baseUrl) {
		
		return OpenAiApi.builder()
					.baseUrl(baseUrl)
					.apiKey("not-required")
					.build();
	}
	
	// TODO: OpenAiApi doesn't have listModels() - needs custom HTTP implementation
	public static List<?> getModels(OpenAiApi api) {
		if (log.isWarnEnabled()) {
			log.warn("Model listing not implemented for OpenAI API. Returning empty list.");
		}
		return List.of();
	}
	
	
}

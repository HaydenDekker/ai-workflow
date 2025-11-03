package com.hdekker.ai_workflow.ollama;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaApi.Model;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class OllamaAdapterTest {
	
	Logger log = LoggerFactory.getLogger(OllamaAdapterTest.class);
	
	public static final String TEST_ENDPOINT_OLLAMA = "http://192.168.2.108:11434";

	@Autowired
	OllamaInstanceAdapter ollamaInstanceAdapter;

	@Test
	public void givenOllamaEndpoint_ExpectBuilderReturnsChatClient() {
		
		OllamaApi api = ollamaInstanceAdapter.createAPI(TEST_ENDPOINT_OLLAMA);
		
		List<Model> models = api.listModels().models();
		
		assertThat(models)
			.hasSizeGreaterThan(0);
		
		models.forEach(m-> log.info(m.toString()));
		
		
	}

	
}

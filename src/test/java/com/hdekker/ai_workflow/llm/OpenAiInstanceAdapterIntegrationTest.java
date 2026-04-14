package com.hdekker.ai_workflow.llm;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaApi.Model;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.hdekker.ai_workflow.TestProfiles;

/**
 * Integration test requiring a real Ollama server at http://0.0.0.0:11434
 * 
 * This test makes actual HTTP calls to list models and validate connectivity.
 * Disabled by default - enable via profile or remove @Disabled annotation.
 */
@Tag("integration")
@Disabled
@SpringBootTest(classes = {
	OpenAiInstanceConfiguration.class,
	OpenAiInstanceConfigurationProperties.class
}, properties = {
	"app.ai.endpoint=http://127.0.0.1:11434",
	"app.ai.model=gemma3:4b",
	"spring.ai.ollama.base-url=http://127.0.0.1:11434"
})
@ActiveProfiles(TestProfiles.RESOURCES_TEST_FOLDER)
public class OpenAiInstanceAdapterIntegrationTest {
	
	private static final Logger log = LoggerFactory.getLogger(OpenAiInstanceAdapterIntegrationTest.class);
	
	public static final String TEST_ENDPOINT_OPENAI = "http://0.0.0.0:11434";

	@Test
	public void givenOpenAiEndpoint_ExpectBuilderReturnsChatClient() {
		
		OllamaApi api = OpenAiInstanceAdapterUtils.createApi(TEST_ENDPOINT_OPENAI);
		
		List<Model> models = api.listModels().models();
		
		assertThat(models)
			.hasSizeGreaterThan(0);
		
		
		List<OllamaChatModel> chatModels = OpenAiInstanceAdapterUtils.getModels(api);
		
		assertThat(chatModels)
			.hasSizeGreaterThan(0);
		
		chatModels.forEach(m-> log.info(m.toString()));
		
		
	}


}

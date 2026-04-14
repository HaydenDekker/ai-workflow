package com.hdekker.ai_workflow.llm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(OpenAiInstanceConfigurationProperties.class)
public class OpenAiInstanceConfiguration {

	private static final Logger log = LoggerFactory.getLogger(OpenAiInstanceConfiguration.class);

	@Autowired
	OpenAiInstanceConfigurationProperties openAiInstanceConfigurationProperties;
	
	@Bean
	@org.springframework.context.annotation.Lazy
	public OllamaChatModel openAiChatModel() {
		if (log.isInfoEnabled()) {
			log.info("Creating OllamaChatModel for '{}'. Model validation occurs at runtime, not startup.",
					openAiInstanceConfigurationProperties.model);
		}
		return OllamaChatModel.builder()
				.ollamaApi(OpenAiInstanceAdapterUtils.createApi(openAiInstanceConfigurationProperties.endpoint))
				.defaultOptions(OllamaOptions.builder()
						.model(openAiInstanceConfigurationProperties.model)
						.build())
				.build();
	}

	@Bean
	@ConditionalOnMissingBean(ChatClient.class)
	@org.springframework.context.annotation.Lazy
	public ChatClient chatClient(OllamaChatModel openAiChatModel) {
		return ChatClient.builder(openAiChatModel).build();
	}
	
}

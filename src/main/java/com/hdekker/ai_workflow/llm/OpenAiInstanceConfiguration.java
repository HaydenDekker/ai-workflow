package com.hdekker.ai_workflow.llm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
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
	public OpenAiChatModel openAiChatModel() {
		if (log.isInfoEnabled()) {
			log.info("Creating OpenAiChatModel for '{}'. Model validation occurs at runtime, not startup.",
					openAiInstanceConfigurationProperties.model);
		}
		return OpenAiChatModel.builder()
				.openAiApi(OpenAiInstanceAdapterUtils.createApi(openAiInstanceConfigurationProperties.endpoint))
				.build();
	}

	@Bean
	@ConditionalOnMissingBean(ChatClient.class)
	@org.springframework.context.annotation.Lazy
	public ChatClient chatClient(OpenAiChatModel openAiChatModel) {
		return ChatClient.builder(openAiChatModel).build();
	}
	
}

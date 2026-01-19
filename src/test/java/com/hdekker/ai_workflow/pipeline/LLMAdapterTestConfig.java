package com.hdekker.ai_workflow.pipeline;

import org.mockito.Mockito;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import com.hdekker.ai_workflow.TestProfiles;

import reactor.core.publisher.Flux;

@Configuration
@Profile(TestProfiles.MOCK_LLM_PROMPT_ADAPTER)
public class LLMAdapterTestConfig {

	@Bean
	@Primary
	public ChatClient testChatClient() {
		ChatClient mock = Mockito.mock(ChatClient.class);
		ChatClient.ChatClientRequestSpec requestSpec = Mockito.mock(ChatClient.ChatClientRequestSpec.class);
		ChatClient.StreamResponseSpec streamSpec = Mockito.mock(ChatClient.StreamResponseSpec.class);

		Mockito.when(mock.prompt(Mockito.anyString())).thenAnswer(invocation -> {
			String p = invocation.getArgument(0);
			Mockito.when(requestSpec.stream()).thenReturn(streamSpec);
			Mockito.when(streamSpec.content()).thenReturn(Flux.just(p + " just return the request."));
			return requestSpec;
		});

		return mock;
	}
	
}

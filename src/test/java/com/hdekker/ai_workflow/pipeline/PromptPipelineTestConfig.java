package com.hdekker.ai_workflow.pipeline;

import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import com.hdekker.ai_workflow.TestProfiles;
import reactor.core.publisher.Flux;

@Configuration
@Profile(TestProfiles.FIXED_LLM_TEST_RESPONSE)
public class PromptPipelineTestConfig {
	
	Logger log = LoggerFactory.getLogger(PromptPipelineTestConfig.class);
	
	Boolean prompterCalled = false;
	
	String stub = """
			Just a simple test response as if its from the raw output of the LLM "
			```json 
			[{ 
			   	"className": "LogSubscriberPort",
			   	"compliance": "YES",
			   	"principle": "Single Responsibility Principle",
			   	"task": "Interface definition",
			   	"reason": "The interface defines a single responsibility: to provide a subscribe method for a consumer that receives log messages. It doesn't contain any business logic or complex operations."
			},
			{
				"className": "LogSubscriberPort",
				"compliance": "NO",
				"principle": "Other principle",
				"task": "Interface definition",
				"reason": "The interface defines a single responsibility: to provide a subscribe method for a consumer that receives log messages. It doesn't contain any business logic or complex operations."
			}
			]```
			""";

	public void setPrompterCalled(Boolean wasCalled) {
		prompterCalled = wasCalled;
	}
	
	@Bean
	@Primary
	ChatClient chatClient() {
		ChatClient mock = Mockito.mock(ChatClient.class);
		ChatClient.ChatClientRequestSpec requestSpec = Mockito.mock(ChatClient.ChatClientRequestSpec.class);
		ChatClient.StreamResponseSpec streamSpec = Mockito.mock(ChatClient.StreamResponseSpec.class);

		Mockito.when(mock.prompt(Mockito.anyString())).thenReturn(requestSpec);
		Mockito.when(requestSpec.stream()).thenReturn(streamSpec);
		Mockito.when(streamSpec.content()).thenReturn(Flux.just(stub)
			.doOnNext(res -> setPrompterCalled(true))
			.doOnNext(res -> log.info("Test LLM Called.")));

		return mock;
	}

	
}

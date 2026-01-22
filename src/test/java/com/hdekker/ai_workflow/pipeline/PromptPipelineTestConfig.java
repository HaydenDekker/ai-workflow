package com.hdekker.ai_workflow.pipeline;

import java.util.ArrayList;
import java.util.List;

import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import reactor.core.publisher.Flux;

@Deprecated
@TestConfiguration
/**
 * @deprecated Use {@link com.hdekker.ai_workflow.test.pipeline.mock.ChatClientMockBuilder.ConfigurableChatClientMock} instead.
 * Migration example:
 * <pre>
 * // Old approach with PromptPipelineTestConfig
 * @Autowired
 * PromptPipelineTestConfig config;
 *
 * @BeforeEach
 * void setup() {
 *     config.setMockResponses(responses);
 * }
 *
 * // New approach with ChatClientMockBuilder
 * ChatClientMockBuilder.ConfigurableChatClientMock mockBuilder;
 *
 * @BeforeEach
 * void setup() {
 *     mockBuilder = ChatClientMockBuilder.forParameterizedTesting(defaultResponse);
 *     mockBuilder.setMockResponses(responses);
 *     ChatClient chatClient = mockBuilder.build();
 *     // Use chatClient in test
 * }
 * </pre>
 */
public class PromptPipelineTestConfig {

	Logger log = LoggerFactory.getLogger(PromptPipelineTestConfig.class);
	
	Boolean prompterCalled = false;
	
	// Dynamic response storage for parameterized tests
	private final List<String> mockResponses = new ArrayList<>();
	private int currentResponseIndex = 0;
	
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

	@Deprecated
	public void setPrompterCalled(Boolean wasCalled) {
		prompterCalled = wasCalled;
	}

	/**
	 * Set mock responses for parameterized testing.
	 * Each call to the LLM will return the next response in the list.
	 * @deprecated Use {@link com.hdekker.ai_workflow.test.pipeline.mock.ChatClientMockBuilder.ConfigurableChatClientMock#setMockResponses(List)} instead
	 */
	@Deprecated
	public void setMockResponses(List<String> responses) {
		this.mockResponses.clear();
		this.mockResponses.addAll(responses);
		this.currentResponseIndex = 0;
	}
	
	/**
	 * Set mock responses from array for reducer testing.
	 * @deprecated Use {@link com.hdekker.ai_workflow.test.pipeline.mock.ChatClientMockBuilder.ConfigurableChatClientMock#setMockResponses(String[])} instead
	 */
	@Deprecated
	public void setMockResponses(String[] responses) {
		this.mockResponses.clear();
		this.mockResponses.addAll(java.util.Arrays.asList(responses));
		this.currentResponseIndex = 0;
	}
	
	/**
	 * Set a single mock response (for backward compatibility).
	 * @deprecated Use {@link com.hdekker.ai_workflow.test.pipeline.mock.ChatClientMockBuilder.ConfigurableChatClientMock#setMockResponse(String)} instead
	 */
	@Deprecated
	public void setMockResponse(String response) {
		this.mockResponses.clear();
		this.mockResponses.add(response);
		this.currentResponseIndex = 0;
	}
	
	/**
	 * Get the next mock response from the list.
	 * If no responses are set, returns the default stub.
	 */
	private String getNextMockResponse() {
		if (mockResponses.isEmpty()) {
			return stub;
		}
		if (currentResponseIndex >= mockResponses.size()) {
			currentResponseIndex = 0; // Loop back to first response
		}
		return mockResponses.get(currentResponseIndex++);
	}
	
	/**
	 * Reset the response index for test isolation.
	 * @deprecated Use {@link com.hdekker.ai_workflow.test.pipeline.mock.ChatClientMockBuilder.ConfigurableChatClientMock#reset()} instead
	 */
	@Deprecated
	public void resetResponses() {
		this.currentResponseIndex = 0;
	}
	
	// TODO bring into ChatMock
	@Bean
	ChatClient chatClient() {
		ChatClient mock = Mockito.mock(ChatClient.class);
		ChatClient.ChatClientRequestSpec requestSpec = Mockito.mock(ChatClient.ChatClientRequestSpec.class);
		ChatClient.StreamResponseSpec streamSpec = Mockito.mock(ChatClient.StreamResponseSpec.class);

		Mockito.when(mock.prompt(Mockito.anyString())).thenReturn(requestSpec);
		Mockito.when(requestSpec.stream()).thenReturn(streamSpec);
		Mockito.when(streamSpec.content()).thenReturn(Flux.defer(() -> Flux.just(getNextMockResponse()))
			.doOnNext(res -> setPrompterCalled(true))
			.doOnNext(res -> log.info("Test LLM Called with response: {}", res.substring(0, Math.min(50, res.length())))));

		return mock;
	}

	
}

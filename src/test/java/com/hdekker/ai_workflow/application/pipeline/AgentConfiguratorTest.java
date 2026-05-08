package com.hdekker.ai_workflow.application.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import java.util.Optional;
import java.util.function.Consumer;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.hdekker.ai_workflow.TestData;
import com.hdekker.ai_workflow.application.pipeline.AgentObserverUseCase;
import com.hdekker.ai_workflow.domain.agent.AgentDefinition;
import com.hdekker.ai_workflow.domain.file.FileHistory;
import com.hdekker.ai_workflow.domain.file.FileMetadata;
import com.hdekker.ai_workflow.domain.prompt.PromptResponse;
import com.hdekker.ai_workflow.domain.shared.FileHash;
import com.hdekker.ai_workflow.test.harness.mock.ChatClientMockBuilder;

import org.springframework.ai.chat.client.ChatClient;
import reactor.core.publisher.Flux;

/*
 *  To take the users list of graph structures and initialise
 *  the edges in memory.
 * 
 */
public class AgentConfiguratorTest {
    
    AgentConfigurator configurator;
    AgentObserverUseCase observerMock;
    
    String expectedMockResult = "This is the expected result";
    
    Boolean persistCalled = false;
    
    @BeforeEach
    public void init() {
        
        String mockFileBody = "This is an example file input body";
        
        FileHistory fh = new FileHistory(
                new FileMetadata(
                        "/config/doco.txt", 
                        mockFileBody,
                        FileHash.hash(mockFileBody)), 
                        Optional.empty());
        
        ChatClient chatClient = ChatClientMockBuilder.createMock(expectedMockResult);
        
        Consumer<PromptResponse> persister = (pr) -> {
            persistCalled = true;
        };
        
        observerMock = mock(AgentObserverUseCase.class);
        
        configurator = new AgentConfigurator(
                Flux.just(fh),
                chatClient,
                persister,
                null,
                observerMock);
    }
    
    
    @Test
    public void givenAgentWithSingleStage_ExpectSingleFluxReturned() {
        
        AgentDefinition agent = TestData.basicPrompt();
        
        Flux<PromptResponse> flux = configurator.configure(agent);
        
        PromptResponse pr = flux.blockFirst(Duration.ofSeconds(3));
        
        assertThat(pr.prompt())
            .isEqualTo(agent);
        
        assertThat(pr.response())
            .isEqualTo(expectedMockResult);
        
        assertThat(persistCalled)
            .isTrue();
        
    }
}

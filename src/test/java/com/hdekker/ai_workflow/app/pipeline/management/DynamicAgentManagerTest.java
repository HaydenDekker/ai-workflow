package com.hdekker.ai_workflow.app.pipeline.management;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import com.hdekker.ai_workflow.TestData;
import com.hdekker.ai_workflow.files.FileHash;
import com.hdekker.ai_workflow.files.FileHistory;
import com.hdekker.ai_workflow.files.FileScanner;
import com.hdekker.ai_workflow.files.FileWriter;
import com.hdekker.ai_workflow.files.domain.FileMetadata;
import com.hdekker.ai_workflow.pipeline.domain.AgentDefinition;
import com.hdekker.ai_workflow.rest.dto.AgentInfo;
import com.hdekker.ai_workflow.test.pipeline.mock.ChatClientMockBuilder;

import reactor.core.publisher.Flux;

public class DynamicAgentManagerTest {

    DynamicAgentManager manager;

    String expectedMockResult = "This is the expected result";

    boolean persistCalled = false;

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

        FileScanner fileScanner = mock(FileScanner.class);
        when(fileScanner.flux()).thenReturn(Flux.just(fh));

        FileWriter fileWriter = mock(FileWriter.class);
        when(fileWriter.createPersister(any(Path.class))).thenReturn((pr) -> {
            persistCalled = true;
        });

        Path outputDirectory = Path.of("/test/output");

        manager = new DynamicAgentManager(
                fileScanner,
                fileWriter,
                outputDirectory,
                chatClient);
    }

    @Test
    public void givenEmptyYAMLAgents_ExpectNoAgentsInitialized() {
        manager.initializeFromYAML(List.of());

        List<AgentInfo> agents = manager.listAgents();
        assertThat(agents).isEmpty();
    }

    @Test
    public void givenYAMLAgents_ExpectAgentsInitialized() {
        AgentDefinition agent = TestData.basicPrompt();

        manager.initializeFromYAML(List.of(agent));

        List<AgentInfo> agents = manager.listAgents();
        assertThat(agents).hasSize(1);

        AgentInfo info = agents.get(0);
        assertThat(info.id()).isEqualTo(agent.title());
        assertThat(info.definition().title()).isEqualTo(agent.title());
        assertThat(info.definition().agentType()).isEqualTo(agent.agentType());
        assertThat(info.source()).isEqualTo("YAML");
        assertThat(info.active()).isTrue();
    }

    @Test
    public void givenDynamicAgentAdded_ExpectAgentCreatedAndListed() {
        AgentDefinition agent = TestData.basicPrompt();

        AgentInfo info = manager.addDynamicAgent(agent);

        assertThat(info.definition().title()).isEqualTo(agent.title());
        assertThat(info.definition().agentType()).isEqualTo(agent.agentType());
        assertThat(info.source()).isEqualTo("DYNAMIC");
        assertThat(info.active()).isTrue();

        List<AgentInfo> agents = manager.listAgents();
        assertThat(agents).hasSize(1);
        assertThat(agents.get(0).id()).isEqualTo(info.id());
    }

    @Test
    public void givenAgentRemoved_ExpectAgentNotListed() {
        AgentDefinition agent = TestData.basicPrompt();

        AgentInfo info = manager.addDynamicAgent(agent);
        assertThat(manager.listAgents()).hasSize(1);

        manager.removeAgent(info.id());
        assertThat(manager.listAgents()).isEmpty();
    }

    @Test
    public void givenNonExistentAgentRemoved_ExpectNoError() {
        manager.removeAgent("non-existent-id");
        // Should not throw exception
    }

    @Test
    public void givenMultipleAgents_ExpectAllListed() {
        AgentDefinition agent1 = TestData.basicPrompt();
        AgentDefinition agent2 = new AgentDefinition(".*\\.txt", "TestAgent2", "Test body 2", "Map", "Test structure 2", "output-{filename}");

        AgentInfo info1 = manager.addDynamicAgent(agent1);
        AgentInfo info2 = manager.addDynamicAgent(agent2);

        List<AgentInfo> agents = manager.listAgents();
        assertThat(agents).hasSize(2);

        List<String> ids = agents.stream().map(AgentInfo::id).toList();
        assertThat(ids).contains(info1.id(), info2.id());
    }

    @Test
    public void givenYAMLandDynamicAgents_ExpectAllListed() {
        AgentDefinition yamlAgent = TestData.basicPrompt();
        AgentDefinition dynamicAgent = new AgentDefinition(".*\\.md", "DynamicAgent", "Dynamic body", "Map", "Dynamic structure", "dynamic-{filename}");

        manager.initializeFromYAML(List.of(yamlAgent));
        manager.addDynamicAgent(dynamicAgent);

        List<AgentInfo> agents = manager.listAgents();
        assertThat(agents).hasSize(2);

        List<String> sources = agents.stream().map(AgentInfo::source).toList();
        assertThat(sources).contains("YAML", "DYNAMIC");
    }
}

package com.hdekker.ai_workflow.app.pipeline.management;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec;
import org.springframework.ai.chat.client.ChatClient.StreamResponseSpec;

import com.hdekker.ai_workflow.TestData;
import com.hdekker.ai_workflow.app.pipeline.management.DynamicPipelineManager;
import com.hdekker.ai_workflow.files.FileHash;
import com.hdekker.ai_workflow.files.FileHistory;
import com.hdekker.ai_workflow.files.FileScanner;
import com.hdekker.ai_workflow.files.FileWriter;
import com.hdekker.ai_workflow.files.domain.FileMetadata;
import com.hdekker.ai_workflow.pipeline.domain.AgentDefinition;
import com.hdekker.ai_workflow.prompt.PromptResponse;
import com.hdekker.ai_workflow.rest.dto.PipelineInfo;
import com.hdekker.ai_workflow.test.pipeline.mock.ChatClientMockBuilder;

import reactor.core.publisher.Flux;

public class DynamicPipelineManagerTest {

    DynamicPipelineManager manager;

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

        ChatClient chatClient = ChatClientMockBuilder.forMapAdapter(expectedMockResult);

        FileScanner fileScanner = mock(FileScanner.class);
        when(fileScanner.flux()).thenReturn(Flux.just(fh));

        FileWriter fileWriter = mock(FileWriter.class);
        when(fileWriter.createPersister(any(Path.class))).thenReturn((pr) -> {
            persistCalled = true;
        });

        Path outputDirectory = Path.of("/test/output");

        manager = new DynamicPipelineManager(
                fileScanner,
                fileWriter,
                outputDirectory,
                chatClient);
    }

    @Test
    public void givenEmptyYAMLAgents_ExpectNoPipelinesInitialized() {
        manager.initializeFromYAML(List.of());

        List<PipelineInfo> pipelines = manager.listPipelines();
        assertThat(pipelines).isEmpty();
    }

    @Test
    public void givenYAMLAgents_ExpectPipelinesInitialized() {
        AgentDefinition agent = TestData.basicPrompt();

        manager.initializeFromYAML(List.of(agent));

        List<PipelineInfo> pipelines = manager.listPipelines();
        assertThat(pipelines).hasSize(1);

        PipelineInfo info = pipelines.get(0);
        assertThat(info.id()).isEqualTo(agent.title());
        assertThat(info.agentDefinition().title()).isEqualTo(agent.title());
        assertThat(info.agentDefinition().agentType()).isEqualTo(agent.agentType());
        assertThat(info.source()).isEqualTo("YAML");
        assertThat(info.active()).isTrue();
    }

    @Test
    public void givenDynamicPipelineAdded_ExpectPipelineCreatedAndListed() {
        AgentDefinition agent = TestData.basicPrompt();

        PipelineInfo info = manager.addDynamicPipeline(agent);

        assertThat(info.agentDefinition().title()).isEqualTo(agent.title());
        assertThat(info.agentDefinition().agentType()).isEqualTo(agent.agentType());
        assertThat(info.source()).isEqualTo("DYNAMIC");
        assertThat(info.active()).isTrue();

        List<PipelineInfo> pipelines = manager.listPipelines();
        assertThat(pipelines).hasSize(1);
        assertThat(pipelines.get(0).id()).isEqualTo(info.id());
    }

    @Test
    public void givenPipelineRemoved_ExpectPipelineNotListed() {
        AgentDefinition agent = TestData.basicPrompt();

        PipelineInfo info = manager.addDynamicPipeline(agent);
        assertThat(manager.listPipelines()).hasSize(1);

        manager.removePipeline(info.id());
        assertThat(manager.listPipelines()).isEmpty();
    }

    @Test
    public void givenNonExistentPipelineRemoved_ExpectNoError() {
        manager.removePipeline("non-existent-id");
        // Should not throw exception
    }

    @Test
    public void givenMultiplePipelines_ExpectAllListed() {
        AgentDefinition agent1 = TestData.basicPrompt();
        AgentDefinition agent2 = new AgentDefinition(".*\\.txt", "TestAgent2", "Test body 2", "Map", "Test structure 2", "output-{filename}");

        PipelineInfo info1 = manager.addDynamicPipeline(agent1);
        PipelineInfo info2 = manager.addDynamicPipeline(agent2);

        List<PipelineInfo> pipelines = manager.listPipelines();
        assertThat(pipelines).hasSize(2);

        List<String> ids = pipelines.stream().map(PipelineInfo::id).toList();
        assertThat(ids).contains(info1.id(), info2.id());
    }

    @Test
    public void givenYAMLandDynamicPipelines_ExpectAllListed() {
        AgentDefinition yamlAgent = TestData.basicPrompt();
        AgentDefinition dynamicAgent = new AgentDefinition(".*\\.md", "DynamicAgent", "Dynamic body", "Map", "Dynamic structure", "dynamic-{filename}");

        manager.initializeFromYAML(List.of(yamlAgent));
        manager.addDynamicPipeline(dynamicAgent);

        List<PipelineInfo> pipelines = manager.listPipelines();
        assertThat(pipelines).hasSize(2);

        List<String> sources = pipelines.stream().map(PipelineInfo::source).toList();
        assertThat(sources).contains("YAML", "DYNAMIC");
    }
}
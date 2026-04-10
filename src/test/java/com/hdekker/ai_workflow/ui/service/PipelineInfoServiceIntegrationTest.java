package com.hdekker.ai_workflow.ui.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.reactive.function.client.WebClient;
import com.hdekker.ai_workflow.app.pipeline.management.DynamicPipelineManager;
import com.hdekker.ai_workflow.pipeline.domain.AgentDefinition;
import com.hdekker.ai_workflow.rest.PipelineRestController;
import com.hdekker.ai_workflow.rest.dto.PipelineInfo;

import reactor.test.StepVerifier;

/**
 * Integration test for PipelineInfoService to verify it can communicate
 * with the REST endpoints and handle responses correctly.
 * 
 * This test uses WebFluxTest to load only the web layer components,
 * avoiding the need for full application context with Ollama dependencies.
 */
//@WebFluxTest(controllers = PipelineRestController.class)
@ContextConfiguration(classes = { PipelineInfoService.class, PipelineRestController.class })
public class PipelineInfoServiceIntegrationTest {

    @Autowired
    private PipelineInfoService pipelineInfoService;

    @MockitoBean
    private DynamicPipelineManager dynamicPipelineManager;

    @Autowired
    private WebClient.Builder webClientBuilder;

    private AgentDefinition testAgent;
    private PipelineInfo testPipeline;

    @BeforeEach
    void setUp() {
        testAgent = new AgentDefinition(".*\\.java$", "Test Agent", "Test Body", "Map", "output.md", "template.md");
        testPipeline = new PipelineInfo("test-id-123", testAgent, LocalDateTime.now(), true, "DYNAMIC");
    }

    @Test
    public void givenPipelineInfoService_whenGetAllPipelineInfos_thenReturnPipelineList() {
        // Given - mock the DynamicPipelineManager to return test data
        when(dynamicPipelineManager.listPipelines()).thenReturn(List.of(testPipeline));

        // When & Then - test the service can fetch data via REST endpoints
        StepVerifier.create(pipelineInfoService.getAllPipelineInfos())
            .assertNext(pipelineInfos -> {
                assertThat(pipelineInfos).isNotNull();
                assertThat(pipelineInfos).hasSize(1);
                
                PipelineInfo fetchedPipeline = pipelineInfos.get(0);
                assertThat(fetchedPipeline.id()).isEqualTo(testPipeline.id());
                assertThat(fetchedPipeline.agentDefinition().title()).isEqualTo(testAgent.title());
                assertThat(fetchedPipeline.agentDefinition().agentType()).isEqualTo(testAgent.agentType());
                assertThat(fetchedPipeline.source()).isEqualTo("DYNAMIC");
            })
            .verifyComplete();
    }

    @Test
    public void givenNoPipelines_whenGetAllPipelineInfos_thenReturnEmptyList() {
        // Given - mock empty pipeline list
        when(dynamicPipelineManager.listPipelines()).thenReturn(List.of());

        // When & Then - service should return empty list gracefully
        StepVerifier.create(pipelineInfoService.getAllPipelineInfos())
            .assertNext(pipelineInfos -> {
                assertThat(pipelineInfos).isNotNull();
                assertThat(pipelineInfos).isEmpty();
            })
            .verifyComplete();
    }

    @Test
    public void givenExistingPipeline_whenDeletePipeline_thenDeleteSuccessfully() {
        // Given - mock pipeline exists
        when(dynamicPipelineManager.listPipelines()).thenReturn(List.of(testPipeline));
        // When & Then - delete should complete without errors
        StepVerifier.create(pipelineInfoService.deletePipeline(testPipeline.id()))
            .verifyComplete();
    }

    @Test
    public void givenNonExistentPipeline_whenDeletePipeline_thenHandleGracefully() {
        // Given - mock pipeline doesn't exist
        when(dynamicPipelineManager.listPipelines()).thenReturn(List.of());

        // When & Then - delete should complete without errors (service handles not found)
        StepVerifier.create(pipelineInfoService.deletePipeline("non-existent-id"))
            .verifyComplete();
    }

    @Test
    public void givenService_whenMultipleCalls_thenHandleConsistently() {
        // Given - set up test data
        AgentDefinition agent2 = new AgentDefinition(".*\\.md$", "Agent 2", "Body 2", "Reducer", "output2.md", "template2.md");
        PipelineInfo pipeline2 = new PipelineInfo("test-id-456", agent2, LocalDateTime.now(), true, "YAML");
        List<PipelineInfo> pipelines = List.of(testPipeline, pipeline2);
        
        when(dynamicPipelineManager.listPipelines()).thenReturn(pipelines);

        // When & Then - multiple calls should work consistently
        StepVerifier.create(pipelineInfoService.getAllPipelineInfos())
            .assertNext(firstCall -> {
                assertThat(firstCall).hasSize(2);
                assertThat(firstCall.get(0).id()).isEqualTo(testPipeline.id());
                assertThat(firstCall.get(1).id()).isEqualTo(pipeline2.id());
            })
            .verifyComplete();

        // Second call should also work
        StepVerifier.create(pipelineInfoService.getAllPipelineInfos())
            .assertNext(secondCall -> {
                assertThat(secondCall).hasSize(2);
            })
            .verifyComplete();
    }
}
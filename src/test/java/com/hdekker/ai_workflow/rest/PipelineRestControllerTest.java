package com.hdekker.ai_workflow.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.hdekker.ai_workflow.TestData;
import com.hdekker.ai_workflow.pipeline.domain.AgentDefinition;
import com.hdekker.ai_workflow.pipeline.management.DynamicPipelineManager;
import com.hdekker.ai_workflow.rest.dto.PipelineInfo;

@WebFluxTest(PipelineRestController.class)
public class PipelineRestControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    private DynamicPipelineManager dynamicPipelineManager;

    @Test
    public void givenValidAgentDefinition_whenPostPipeline_thenPipelineCreated() {
        // Given
        AgentDefinition agentDef = TestData.basicPrompt();
        PipelineInfo expectedInfo = new PipelineInfo("test-id", agentDef.title(), agentDef.agentType(), java.time.LocalDateTime.now(), true, "DYNAMIC");

        when(dynamicPipelineManager.addDynamicPipeline(any(AgentDefinition.class))).thenReturn(expectedInfo);

        // When & Then - POST to create pipeline
        PipelineInfo response = webTestClient.post()
            .uri("/api/pipelines")
            .bodyValue(agentDef)
            .exchange()
            .expectStatus().isOk()
            .expectBody(PipelineInfo.class)
            .returnResult()
            .getResponseBody();

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo("test-id");
        assertThat(response.title()).isEqualTo(agentDef.title());
        assertThat(response.agentType()).isEqualTo(agentDef.agentType());
        assertThat(response.source()).isEqualTo("DYNAMIC");
    }

    @Test
    public void whenGetPipelines_thenListOfPipelinesReturned() {
        // Given
        List<PipelineInfo> pipelines = List.of(
            new PipelineInfo("id1", "Title1", "Map", java.time.LocalDateTime.now(), true, "DYNAMIC"),
            new PipelineInfo("id2", "Title2", "Reducer", java.time.LocalDateTime.now(), true, "YAML")
        );
        when(dynamicPipelineManager.listPipelines()).thenReturn(pipelines);

        // When & Then - GET pipelines
        List<PipelineInfo> response = webTestClient.get()
            .uri("/api/pipelines")
            .exchange()
            .expectStatus().isOk()
            .expectBodyList(PipelineInfo.class)
            .returnResult()
            .getResponseBody();

        assertThat(response).hasSize(2);
        assertThat(response.get(0).id()).isEqualTo("id1");
        assertThat(response.get(1).id()).isEqualTo("id2");
    }

    @Test
    public void givenExistingPipeline_whenDeletePipeline_thenPipelineRemoved() {
        // Given
        String pipelineId = "test-id";

        // When & Then - DELETE pipeline
        webTestClient.delete()
            .uri("/api/pipelines/{id}", pipelineId)
            .exchange()
            .expectStatus().isNoContent();
    }
}
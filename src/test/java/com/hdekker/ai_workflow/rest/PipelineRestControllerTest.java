package com.hdekker.ai_workflow.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.hdekker.ai_workflow.TestData;
import com.hdekker.ai_workflow.app.pipeline.management.DynamicPipelineManager;
import com.hdekker.ai_workflow.pipeline.domain.AgentDefinition;
import com.hdekker.ai_workflow.rest.dto.PipelineInfo;

import tools.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.springframework.http.MediaType;


@WebMvcTest(PipelineRestController.class)
public class PipelineRestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private DynamicPipelineManager dynamicPipelineManager;

    @Test
    public void givenValidAgentDefinition_whenPostPipeline_thenPipelineCreated() throws Exception {
        // Given
        AgentDefinition agentDef = TestData.basicPrompt();
        PipelineInfo expectedInfo = new PipelineInfo("test-id", agentDef, java.time.LocalDateTime.now(), true, "DYNAMIC");

        when(dynamicPipelineManager.addDynamicPipeline(any(AgentDefinition.class))).thenReturn(expectedInfo);

        // When & Then - POST to create pipeline
        String responseContent = mockMvc.perform(post("/api/pipelines")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(agentDef)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

        PipelineInfo response = objectMapper.readValue(responseContent, PipelineInfo.class);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo("test-id");
        assertThat(response.agentDefinition().title()).isEqualTo(agentDef.title());
        assertThat(response.agentDefinition().agentType()).isEqualTo(agentDef.agentType());
        assertThat(response.source()).isEqualTo("DYNAMIC");
    }

    @Test
    public void whenGetPipelines_thenListOfPipelinesReturned() throws Exception {
        // Given
        AgentDefinition agent1 = new AgentDefinition(".*\\.txt$", "Title1", "Body1", "Map", "Output1", "Template1");
        AgentDefinition agent2 = new AgentDefinition(".*\\.md$", "Title2", "Body2", "Reducer", "Output2", "Template2");
        List<PipelineInfo> pipelines = List.of(
            new PipelineInfo("id1", agent1, java.time.LocalDateTime.now(), true, "DYNAMIC"),
            new PipelineInfo("id2", agent2, java.time.LocalDateTime.now(), true, "YAML")
        );
        when(dynamicPipelineManager.listPipelines()).thenReturn(pipelines);

        // When & Then - GET pipelines
        String responseContent = mockMvc.perform(get("/api/pipelines"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

        List<PipelineInfo> response = objectMapper.readValue(responseContent, objectMapper.getTypeFactory().constructCollectionType(List.class, PipelineInfo.class));

        assertThat(response).hasSize(2);
        assertThat(response.get(0).id()).isEqualTo("id1");
        assertThat(response.get(1).id()).isEqualTo("id2");
    }

    @Test
    public void givenExistingPipeline_whenDeletePipeline_thenPipelineRemoved() throws Exception {
        // Given
        String pipelineId = "test-id";

        // When & Then - DELETE pipeline
        mockMvc.perform(delete("/api/pipelines/{id}", pipelineId))
            .andExpect(status().isNoContent());
    }
}
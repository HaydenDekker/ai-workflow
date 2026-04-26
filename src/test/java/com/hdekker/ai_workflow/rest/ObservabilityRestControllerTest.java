package com.hdekker.ai_workflow.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.hdekker.ai_workflow.rest.dto.AdapterStatus;
import com.hdekker.ai_workflow.rest.dto.LLMStatus;
import com.hdekker.ai_workflow.usecases.AgentStatusUsecase;

@WebMvcTest(ObservabilityRestController.class)
public class ObservabilityRestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AgentStatusUsecase llmStatusService;

    @Test
    public void givenStatusExists_whenGetLLMStatus_thenReturnsStatusList() throws Exception {
        // Given
        LLMStatus status = new LLMStatus(
            "http://192.168.2.108:11434",
            "gemma3:27b",
            AdapterStatus.UP,
            LocalDateTime.now(),
            3,
            List.of("qwen3-coder6", "gemma3:27b", "llama3.1"),
            null
        );
        when(llmStatusService.getCurrentStatus()).thenReturn(List.of(status));

        // When & Then
        mockMvc.perform(get("/api/observability/llm-status")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.length()").value(1))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$[0].endpoint").value("http://192.168.2.108:11434"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$[0].status").value("UP"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$[0].modelCount").value(3));
    }

    @Test
    public void whenGetLLMStatusNoData_thenReturnsEmptyList() throws Exception {
        // Given
        when(llmStatusService.getCurrentStatus()).thenReturn(List.of());

        // When & Then
        mockMvc.perform(get("/api/observability/llm-status")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.length()").value(0));
    }

    @Test
    public void whenTriggerPoll_thenReturnsUpdatedStatus() throws Exception {
        // Given
        LLMStatus status = new LLMStatus(
            "http://192.168.2.108:11434",
            "gemma3:27b",
            AdapterStatus.UP,
            LocalDateTime.now(),
            1,
            List.of("qwen3-coder6"),
            null
        );
        when(llmStatusService.triggerPoll()).thenReturn(List.of(status));

        // When & Then
        MvcResult result = mockMvc.perform(post("/api/observability/llm-status/poll")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
            .andReturn();

        String responseContent = result.getResponse().getContentAsString();
        assertThat(responseContent).contains("qwen3-coder6");
        assertThat(responseContent).contains("UP");
    }

    @Test
    public void whenTriggerPollNoEndpoint_thenReturnsEmptyList() throws Exception {
        // Given
        when(llmStatusService.triggerPoll()).thenReturn(List.of());

        // When & Then
        mockMvc.perform(post("/api/observability/llm-status/poll")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.length()").value(0));
    }

    @Test
    public void givenMultipleEndpoints_whenGetLLMStatus_thenReturnsAll() throws Exception {
        // Given
        LLMStatus endpoint1 = new LLMStatus(
            "http://192.168.2.108:11434",
            "gemma3:27b",
            AdapterStatus.UP,
            LocalDateTime.now(),
            3,
            List.of(),
            null
        );
        LLMStatus endpoint2 = new LLMStatus(
            "http://10.0.0.5:8080",
            "llama3.1",
            AdapterStatus.DOWN,
            LocalDateTime.now().minusHours(2),
            0,
            List.of(),
            "Connection refused"
        );
        when(llmStatusService.getCurrentStatus()).thenReturn(List.of(endpoint1, endpoint2));

        // When & Then
        mockMvc.perform(get("/api/observability/llm-status")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.length()").value(2))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$[0].status").value("UP"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$[1].status").value("DOWN"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$[1].errorMessage").value("Connection refused"));
    }
}

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
import com.hdekker.ai_workflow.app.pipeline.management.DynamicAgentManager;
import com.hdekker.ai_workflow.pipeline.domain.AgentDefinition;
import com.hdekker.ai_workflow.rest.dto.AgentInfo;


@WebMvcTest(AgentRestController.class)
public class AgentRestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DynamicAgentManager dynamicAgentManager;

    @Test
    public void givenValidAgentDefinition_whenPostAgent_thenAgentCreated() throws Exception {
        // Given
        AgentDefinition agentDef = TestData.basicPrompt();
        AgentInfo expectedInfo = new AgentInfo("test-id", agentDef, java.time.LocalDateTime.now(), true, "DYNAMIC");

        when(dynamicAgentManager.addDynamicAgent(any(AgentDefinition.class))).thenReturn(expectedInfo);

        // When & Then - POST to create agent
        String responseContent = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/agents")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"fileInputRegex\":\".*\\\\.java$\",\"title\":\"Test\",\"body\":\"test.md\",\"agentType\":\"Map\",\"outputStructure\":\"test-output.md\",\"outputFilenameTemplate\":\"output/test/{name}.md\"}"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

        assertThat(responseContent).isNotNull();
        assertThat(responseContent).contains("test-id");
    }

    @Test
    public void whenGetAgents_thenListOfAgentsReturned() throws Exception {
        // Given
        AgentDefinition agent1 = new AgentDefinition(".*\\.txt$", "Title1", "Body1", "Map", "Output1", "Template1");
        AgentDefinition agent2 = new AgentDefinition(".*\\.md$", "Title2", "Body2", "Reducer", "Output2", "Template2");
        List<AgentInfo> agents = List.of(
            new AgentInfo("id1", agent1, java.time.LocalDateTime.now(), true, "DYNAMIC"),
            new AgentInfo("id2", agent2, java.time.LocalDateTime.now(), true, "YAML")
        );
        when(dynamicAgentManager.listAgents()).thenReturn(agents);

        // When & Then - GET agents
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/agents"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.length()").value(2));
    }

    @Test
    public void givenExistingAgent_whenDeleteAgent_thenAgentRemoved() throws Exception {
        // Given
        String agentId = "test-id";

        // When & Then - DELETE agent
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/agents/{id}", agentId))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNoContent());
    }
}

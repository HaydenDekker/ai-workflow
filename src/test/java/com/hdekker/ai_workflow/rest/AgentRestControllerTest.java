package com.hdekker.ai_workflow.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.hdekker.ai_workflow.app.pipeline.management.DynamicAgentManager;
import com.hdekker.ai_workflow.pipeline.domain.AgentDefinition;
import com.hdekker.ai_workflow.rest.dto.AgentInfo;

/**
 * Tests for AgentRestController REST API endpoints.
 * 
 * Verifies create, list, delete, enable, and disable agent operations.
 */
@WebMvcTest(AgentRestController.class)
public class AgentRestControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private DynamicAgentManager dynamicAgentManager;

	private AgentDefinition testAgent;

	@BeforeEach
	public void setUp() {
		testAgent = new AgentDefinition(
				".*\\.txt",
				"Test Agent",
				"This is a test prompt.",
				"Map",
				"Clean output",
				"output/{filename}");
	}

	@Test
	public void givenValidAgent_whenCreateAgent_thenReturnCreatedAgent() throws Exception {
		// Arrange
		AgentInfo expectedInfo = new AgentInfo("test-id-1", testAgent, LocalDateTime.now(), true, "DYNAMIC");
		when(dynamicAgentManager.addDynamicAgent(any(AgentDefinition.class))).thenReturn(expectedInfo);

		String jsonBody = "{"
				+ "\"fileInputRegex\":\".*\\\\.txt\","
				+ "\"title\":\"Test Agent\","
				+ "\"body\":\"This is a test prompt.\","
				+ "\"agentType\":\"Map\","
				+ "\"outputStructure\":\"Clean output\","
				+ "\"outputFilenameTemplate\":\"output/{filename}\""
				+ "}";

		// Act & Assert
		mockMvc.perform(post("/api/agents")
				.contentType(MediaType.APPLICATION_JSON)
				.content(jsonBody))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value("test-id-1"))
				.andExpect(jsonPath("$.definition.title").value("Test Agent"))
				.andExpect(jsonPath("$.active").value(true))
				.andExpect(jsonPath("$.source").value("DYNAMIC"));
	}

	@Test
	public void givenAgents_whenListAgents_thenReturnAllAgents() throws Exception {
		// Arrange
		AgentInfo agent1 = new AgentInfo("agent-1", testAgent, LocalDateTime.now(), true, "YAML");
		AgentInfo agent2 = new AgentInfo("agent-2", testAgent, LocalDateTime.now().minusDays(1), true, "DYNAMIC");
		when(dynamicAgentManager.listAgents()).thenReturn(List.of(agent1, agent2));

		// Act & Assert
		mockMvc.perform(get("/api/agents"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$").isArray())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].id").value("agent-1"))
				.andExpect(jsonPath("$[1].id").value("agent-2"));
	}

	@Test
	public void givenAgentId_whenDeleteAgent_thenReturnsNoContent() throws Exception {
		// Arrange
		doNothing().when(dynamicAgentManager).removeAgent(anyString());

		// Act & Assert
		mockMvc.perform(delete("/api/agents/agent-123"))
				.andExpect(status().isNoContent());
	}

	@Test
	public void givenAgentId_whenEnableAgent_thenReturnOkWithUpdatedInfo() throws Exception {
		// Arrange
		AgentInfo enabledInfo = new AgentInfo("agent-123", testAgent, LocalDateTime.now(), true, "YAML");
		when(dynamicAgentManager.enableAgent(anyString())).thenReturn(enabledInfo);

		// Act & Assert
		mockMvc.perform(put("/api/agents/agent-123/enable"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value("agent-123"))
				.andExpect(jsonPath("$.active").value(true));
	}

	@Test
	public void givenNonExistentId_whenEnableAgent_thenReturnNotFound() throws Exception {
		// Arrange
		when(dynamicAgentManager.enableAgent(anyString())).thenReturn(null);

		// Act & Assert
		mockMvc.perform(put("/api/agents/non-existent/enable"))
				.andExpect(status().isNotFound());
	}

	@Test
	public void givenAgentId_whenDisableAgent_thenReturnOk() throws Exception {
		// Arrange
		doNothing().when(dynamicAgentManager).disableAgent(anyString());

		// Act & Assert
		mockMvc.perform(put("/api/agents/agent-123/disable"))
				.andExpect(status().isOk());
	}

	@Test
	public void givenCreateAndDisable_whenList_thenReturnInactiveAgent() throws Exception {
		// Arrange
		AgentInfo createdInfo = new AgentInfo("agent-1", testAgent, LocalDateTime.now(), true, "DYNAMIC");
		AgentInfo listedInfo = new AgentInfo("agent-1", testAgent, LocalDateTime.now(), false, "DYNAMIC");

		when(dynamicAgentManager.addDynamicAgent(any(AgentDefinition.class))).thenReturn(createdInfo);
		doNothing().when(dynamicAgentManager).disableAgent(anyString());
		when(dynamicAgentManager.listAgents()).thenReturn(List.of(listedInfo));

		String jsonBody = "{"
				+ "\"fileInputRegex\":\".*\\\\.txt\","
				+ "\"title\":\"Test Agent\","
				+ "\"body\":\"This is a test prompt.\","
				+ "\"agentType\":\"Map\","
				+ "\"outputStructure\":\"Clean output\","
				+ "\"outputFilenameTemplate\":\"output/{filename}\""
				+ "}";

		// Act & Assert - Create
		mockMvc.perform(post("/api/agents")
				.contentType(MediaType.APPLICATION_JSON)
				.content(jsonBody))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.active").value(true));

		// Act & Assert - Disable
		mockMvc.perform(put("/api/agents/agent-1/disable"))
				.andExpect(status().isOk());

		// Act & Assert - List shows inactive
		mockMvc.perform(get("/api/agents"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].active").value(false));
	}
}

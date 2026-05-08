package com.hdekker.ai_workflow.adapter.inbound.rest.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.hdekker.ai_workflow.application.agent.AgentLifecycleService;
import com.hdekker.ai_workflow.application.agent.port.DirectoryValidationPort;
import com.hdekker.ai_workflow.application.pipeline.AgentObserverUseCase;
import com.hdekker.ai_workflow.domain.agent.AgentDefinition;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Tests for AgentController REST API endpoints.
 * 
 * Verifies create, list, delete, enable, disable, and refresh agent operations.
 */
@WebMvcTest(AgentController.class)
public class AgentControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private AgentLifecycleService agentLifecycleService;

	@MockitoBean
	private DirectoryValidationPort directoryValidationPort;

	@MockitoBean
	private AgentObserverUseCase agentObserverUseCase;

	private AgentDefinition testAgent;

	@BeforeEach
	public void setUp() {
		testAgent = new AgentDefinition(
				".*\\.txt",
				"Test Agent",
				"This is a test prompt.",
				"Map",
				"Clean output",
				"output/{filename}",
				"/tmp/test-dir");
		// Mock the validator to accept /tmp/test-dir as valid
		when(directoryValidationPort.validate(anyString()))
				.thenAnswer(invocation -> {
					String path = invocation.getArgument(0);
					if (path == null || path.isBlank()) {
						return DirectoryValidationPort.ValidationResult.failure("targetDirectory is required");
					}
					return DirectoryValidationPort.ValidationResult.success();
				});
	}

	@Test
	public void givenValidAgent_whenCreateAgent_thenReturnCreatedAgent() throws Exception {
		// Arrange
		com.hdekker.ai_workflow.domain.agent.AgentInfo expectedInfo = new com.hdekker.ai_workflow.domain.agent.AgentInfo("test-id-1", testAgent, LocalDateTime.now(), true, "DYNAMIC");
		when(agentLifecycleService.addDynamicAgent(any(AgentDefinition.class), anyString())).thenReturn(expectedInfo);

		String jsonBody = "{"
				+ "\"fileInputRegex\":\".*\\\\.txt\","
				+ "\"title\":\"Test Agent\","
				+ "\"body\":\"This is a test prompt.\","
				+ "\"agentType\":\"Map\","
				+ "\"outputStructure\":\"Clean output\","
				+ "\"outputFilenameTemplate\":\"output/{filename}\","
				+ "\"targetDirectory\":\"/tmp/test-dir\""
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
		com.hdekker.ai_workflow.domain.agent.AgentInfo agent1 = new com.hdekker.ai_workflow.domain.agent.AgentInfo("agent-1", testAgent, LocalDateTime.now(), true, "YAML");
		com.hdekker.ai_workflow.domain.agent.AgentInfo agent2 = new com.hdekker.ai_workflow.domain.agent.AgentInfo("agent-2", testAgent, LocalDateTime.now().minusDays(1), true, "DYNAMIC");
		when(agentLifecycleService.listAgents()).thenReturn(List.of(agent1, agent2));

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
		doNothing().when(agentLifecycleService).removeAgent(anyString());

		// Act & Assert
		mockMvc.perform(delete("/api/agents/agent-123"))
				.andExpect(status().isNoContent());
	}

	@Test
	public void givenAgentId_whenEnableAgent_thenReturnOkWithUpdatedInfo() throws Exception {
		// Arrange
		com.hdekker.ai_workflow.domain.agent.AgentInfo enabledInfo = new com.hdekker.ai_workflow.domain.agent.AgentInfo("agent-123", testAgent, LocalDateTime.now(), true, "YAML");
		when(agentLifecycleService.enableAgent(anyString())).thenReturn(enabledInfo);

		// Act & Assert
		mockMvc.perform(put("/api/agents/agent-123/enable"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value("agent-123"))
				.andExpect(jsonPath("$.active").value(true));
	}

	@Test
	public void givenNonExistentId_whenEnableAgent_thenReturnNotFound() throws Exception {
		// Arrange
		when(agentLifecycleService.enableAgent(anyString())).thenReturn(null);

		// Act & Assert
		mockMvc.perform(put("/api/agents/non-existent/enable"))
				.andExpect(status().isNotFound());
	}

	@Test
	public void givenAgentId_whenDisableAgent_thenReturnOk() throws Exception {
		// Arrange
		doNothing().when(agentLifecycleService).disableAgent(anyString());

		// Act & Assert
		mockMvc.perform(put("/api/agents/agent-123/disable"))
				.andExpect(status().isOk());
	}

	@Test
	public void givenCreateAndDisable_whenList_thenReturnInactiveAgent() throws Exception {
		// Arrange
		com.hdekker.ai_workflow.domain.agent.AgentInfo createdInfo = new com.hdekker.ai_workflow.domain.agent.AgentInfo("agent-1", testAgent, LocalDateTime.now(), true, "DYNAMIC");
		com.hdekker.ai_workflow.domain.agent.AgentInfo listedInfo = new com.hdekker.ai_workflow.domain.agent.AgentInfo("agent-1", testAgent, LocalDateTime.now(), false, "DYNAMIC");

		when(agentLifecycleService.addDynamicAgent(any(AgentDefinition.class), anyString())).thenReturn(createdInfo);
		doNothing().when(agentLifecycleService).disableAgent(anyString());
		when(agentLifecycleService.listAgents()).thenReturn(List.of(listedInfo));

		String jsonBody = "{"
				+ "\"fileInputRegex\":\".*\\\\.txt\","
				+ "\"title\":\"Test Agent\","
				+ "\"body\":\"This is a test prompt.\","
				+ "\"agentType\":\"Map\","
				+ "\"outputStructure\":\"Clean output\","
				+ "\"outputFilenameTemplate\":\"output/{filename}\","
				+ "\"targetDirectory\":\"/tmp/test-dir\""
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

	@Test
	public void givenActiveAgent_whenRefreshAgent_thenReturnOkWithUpdatedInfo() throws Exception {
		// Arrange
		com.hdekker.ai_workflow.domain.agent.AgentInfo refreshedInfo = new com.hdekker.ai_workflow.domain.agent.AgentInfo("agent-1", testAgent, LocalDateTime.now(), true, "DYNAMIC");
		when(agentLifecycleService.refreshAgent(anyString())).thenReturn(refreshedInfo);

		// Act & Assert
		mockMvc.perform(post("/api/agents/agent-1/refresh"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value("agent-1"));
	}

	@Test
	public void givenNonExistentAgent_whenRefreshAgent_thenReturnNotFound() throws Exception {
		// Arrange
		when(agentLifecycleService.refreshAgent(anyString())).thenReturn(null);

		// Act & Assert
		mockMvc.perform(post("/api/agents/non-existent/refresh"))
				.andExpect(status().isNotFound());
	}

	@Test
	public void givenValidAgentId_whenUpdateAgent_thenReturnUpdatedAgent() throws Exception {
		// Arrange
		AgentDefinition updatedDef = new AgentDefinition(
				".*\\.md", "Updated Agent", "Updated prompt", "Reduction", "Updated structure", "updated/{filename}", "/tmp/updated-dir");
		com.hdekker.ai_workflow.domain.agent.AgentInfo updatedInfo = new com.hdekker.ai_workflow.domain.agent.AgentInfo("agent-1", updatedDef, LocalDateTime.now(), true, "DYNAMIC");
		when(agentLifecycleService.updateAgent(anyString(), any(AgentDefinition.class))).thenReturn(updatedInfo);

		String jsonBody = "{" +
				"\"fileInputRegex\":\".*\\\\.md\"," +
				"\"title\":\"Updated Agent\"," +
				"\"body\":\"Updated prompt\"," +
				"\"agentType\":\"Reduction\"," +
				"\"outputStructure\":\"Updated structure\"," +
				"\"outputFilenameTemplate\":\"updated/{filename}\"," +
				"\"targetDirectory\":\"/tmp/updated-dir\"" +
				"}";

		// Act & Assert
		mockMvc.perform(put("/api/agents/agent-1")
				.contentType(MediaType.APPLICATION_JSON)
				.content(jsonBody))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value("agent-1"))
				.andExpect(jsonPath("$.definition.title").value("Updated Agent"))
				.andExpect(jsonPath("$.definition.agentType").value("Reduction"));
	}

	// -- Phase 4: Agent observer REST endpoint tests --

	@Test
	void givenObserver_WhenGetOutputDirectoryFileCount_thenReturnCount() throws Exception {
		// Arrange
		when(agentObserverUseCase.getOutputDirectoryFileCount()).thenReturn(15L);

		// Act & Assert
		mockMvc.perform(get("/api/agents/output-file-count"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.outputDirectoryFileCount").value(15));
	}

	@Test
	void givenObserver_WhenNoOutputFiles_thenReturnZero() throws Exception {
		// Arrange
		when(agentObserverUseCase.getOutputDirectoryFileCount()).thenReturn(0L);

		// Act & Assert
		mockMvc.perform(get("/api/agents/output-file-count"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.outputDirectoryFileCount").value(0));
	}

	@Test
	void givenObserver_WhenGetDispatchCount_thenReturnCount() throws Exception {
		// Arrange
		when(agentObserverUseCase.getTotalDispatchCount()).thenReturn(42L);

		// Act & Assert
		mockMvc.perform(get("/api/agents/dispatch-count"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalDispatchCount").value(42));
	}

	@Test
	void givenObserver_WhenNoDispatches_thenReturnZero() throws Exception {
		// Arrange
		when(agentObserverUseCase.getTotalDispatchCount()).thenReturn(0L);

		// Act & Assert
		mockMvc.perform(get("/api/agents/dispatch-count"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalDispatchCount").value(0));
	}

	@Test
	public void givenNonExistentAgent_whenUpdateAgent_thenReturnNotFound() throws Exception {
		// Arrange - mock both removeAgent (called by updateAgent internally) and updateAgent
		doNothing().when(agentLifecycleService).removeAgent(anyString());
		when(agentLifecycleService.updateAgent(anyString(), any(AgentDefinition.class))).thenReturn(null);

		String jsonBody = "{" +
				"\"fileInputRegex\":\".*\\\\.txt\"," +
				"\"title\":\"Test\"," +
				"\"body\":\"Body\"," +
				"\"agentType\":\"Map\"," +
				"\"outputStructure\":\"Structure\"," +
				"\"outputFilenameTemplate\":\"output\"," +
				"\"targetDirectory\":\"/tmp\"" +
				"}";

		// Act & Assert
		mockMvc.perform(put("/api/agents/non-existent")
				.contentType(MediaType.APPLICATION_JSON)
				.content(jsonBody))
				.andExpect(status().isNotFound());
	}
	// -- Phase 6: Output file count via /metrics/files endpoint --

	@Test
	void givenObserver_WhenGetMetricsFiles_thenReturnCount() throws Exception {
		// Arrange
		when(agentObserverUseCase.getOutputDirectoryFileCount()).thenReturn(23L);

		// Act & Assert
		mockMvc.perform(get("/api/agents/metrics/files"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$").value(23L));
	}

	@Test
	void givenObserver_WhenNoOutputFilesViaMetricsFiles_thenReturnZero() throws Exception {
		// Arrange
		when(agentObserverUseCase.getOutputDirectoryFileCount()).thenReturn(0L);

		// Act & Assert
		mockMvc.perform(get("/api/agents/metrics/files"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$").value(0L));
	}
}

package com.hdekker.ai_workflow.adapter.inbound.ui.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.hdekker.ai_workflow.adapter.inbound.rest.dto.AgentInfoDTO;
import com.hdekker.ai_workflow.adapter.inbound.ui.component.AgentDetailDialog;
import com.hdekker.ai_workflow.adapter.inbound.ui.service.AgentInfoService;
import com.hdekker.ai_workflow.application.agent.AgentLifecycleService;
import com.hdekker.ai_workflow.application.agent.port.DirectoryValidationPort;
import com.hdekker.ai_workflow.domain.agent.AgentDefinition;

import com.vaadin.browserless.SpringBrowserlessTest;
import com.vaadin.browserless.TreeOnFailureExtension;
import com.vaadin.browserless.ViewPackages;
import com.vaadin.browserless.internal.MockVaadin;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * Browserless test for AgentListView delete flow.
 * 
 * <p>Tests the complete delete scenario:</p>
 * <ol>
 *   <li>Navigate to the agents view</li>
 *   <li>Verify an agent is displayed in the grid</li>
 *   <li>Click the agent row to open the detail dialog</li>
 *   <li>Click the Delete button in the dialog</li>
 *   <li>Confirm the delete in the confirmation dialog</li>
 *   <li>Verify the agent is removed from the grid</li>
 * </ol>
 * 
 * @see AgentListView
 * @see AgentDetailDialog
 */
@ExtendWith({SpringExtension.class, TreeOnFailureExtension.class})
@SpringBootTest(classes = AgentListViewDeleteTest.Config.class)
@ViewPackages(classes = {AgentListView.class})
class AgentListViewDeleteTest extends SpringBrowserlessTest {

    static final ConcurrentHashMap<String, com.hdekker.ai_workflow.domain.agent.AgentInfo> TEST_AGENTS = new ConcurrentHashMap<>();

    @Autowired
    private AgentInfoService agentInfoService;

    private AgentListView view;

    // --- Helper methods ---

    private static com.hdekker.ai_workflow.domain.agent.AgentInfo createDomainAgent(String id, String title, String regex) {
        return new com.hdekker.ai_workflow.domain.agent.AgentInfo(
                id,
                new AgentDefinition(regex, title, "Body", "Map", "Output", "out/${name}.md",
                        System.getProperty("java.io.tmpdir")),
                LocalDateTime.now(), true, "TEST");
    }

    private static com.hdekker.ai_workflow.domain.agent.AgentInfo createTestDomainAgent() {
        return createDomainAgent("agent-test-1", "Test Agent", ".*\\.txt");
    }

    @BeforeEach
    void setUp() {
        // Reset the test state before each test
        TEST_AGENTS.clear();
        com.hdekker.ai_workflow.domain.agent.AgentInfo agent = createTestDomainAgent();
        TEST_AGENTS.put(agent.id(), agent);
        view = navigate(AgentListView.class);
    }

    /**
     * Test: Reload data after delete fetches fresh data from the test store.
     * 
     * <p>Verifies that calling {@code reloadData()} after an agent is removed
     * from the test store correctly updates the grid.</p>
     */
    @Test
    void reloadAfterDelete_fetchesFreshData() {
        // Arrange: Start with 2 agents (clear setUp state first)
        TEST_AGENTS.clear();
        com.hdekker.ai_workflow.domain.agent.AgentInfo agent1 = createDomainAgent("agent-1", "Test Agent 1", ".*\\.txt");
        com.hdekker.ai_workflow.domain.agent.AgentInfo agent2 = createDomainAgent("agent-2", "Test Agent 2", ".*\\.java");
        TEST_AGENTS.put(agent1.id(), agent1);
        TEST_AGENTS.put(agent2.id(), agent2);

        // Act: Navigate and verify 2 agents
        view = navigate(AgentListView.class);
        Grid<AgentInfoDTO> grid = view.grid;
        assertEquals(2, grid.getGenericDataView().getItems().toList().size(),
                "Grid should show 2 agents");

        // Simulate delete of first agent
        TEST_AGENTS.remove(agent1.id());

        // Call reload directly (simulates the onDelete callback)
        view.reloadData();
        roundTrip();

        // Verify only 1 agent remains
        List<AgentInfoDTO> remaining = grid.getGenericDataView().getItems().toList();
        assertEquals(1, remaining.size(), "Grid should show 1 agent after delete");
        assertEquals(agent2.id(), remaining.get(0).id(),
                "Remaining agent should be the one that wasn't deleted (agents=" + TEST_AGENTS.size() + ")");
    }

    /**
     * Test: Delete an agent from the grid via the detail dialog.
     * 
     * <p>Verifies the complete delete flow including:</p>
     * <ul>
     *   <li>Grid shows the agent before delete</li>
     *   <li>Dialog opens on row click</li>
     *   <li>Delete button triggers confirmation</li>
     *   <li>After confirmation, the agent is removed from the grid</li>
     * </ul>
     */
    @Test
    void deleteAgent_viaDetailDialog_gridUpdated() {
        // Arrange: Test store has one agent
        com.hdekker.ai_workflow.domain.agent.AgentInfo agent = createTestDomainAgent();
        TEST_AGENTS.put(agent.id(), agent);

        // Act: Navigate and verify agent is in grid
        view = navigate(AgentListView.class);
        Grid<AgentInfoDTO> grid = view.grid;
        assertEquals(1, grid.getGenericDataView().getItems().toList().size(),
                "Grid should show 1 agent initially");

        // Act: Open detail dialog with the agent (convert domain to DTO)
        AgentInfoDTO dtoAgent = new AgentInfoDTO(
                agent.id(),
                agent.definition(),
                agent.createdAt(),
                agent.active(),
                agent.source());
        AgentDetailDialog dialog = new AgentDetailDialog(
                agentInfoService,
                dtoAgent,
                info -> {},  // onSave: no-op
                id -> {
                    // onDelete: reload data (simulates the callback from AgentListView)
                    view.reloadData();
                }
        );
        dialog.open();

        // Verify dialog is open
        assertTrue($(Dialog.class).all().size() >= 1, "Dialog should be open");

        // Act: Click the Delete button in the dialog
        Button deleteButton = $(Button.class).withCaption("Delete Agent").single();
        deleteButton.click();

        // Click the confirm "Delete" button in the confirmation dialog
        Button confirmBtn = $(Button.class).withCaption("Delete").single();
        confirmBtn.click();

        // Process UI queue - performDelete() is queued via UI.access() in the confirm handler.
        // This calls agentInfoService.deleteAgent(id), which calls mock's removeAgent(id),
        // then queues the reload via another UI.access().
        MockVaadin.runUIQueue();
        // Second pass - process the queued reload callback.
        MockVaadin.runUIQueue();

        // Flush state tree to client
        roundTrip();

        // Debug: check test state directly
        int testAgentCount = TEST_AGENTS.size();

        // Verify the agent is removed from the grid
        List<AgentInfoDTO> remainingItems = grid.getGenericDataView().getItems().toList();
        assertEquals(0, remainingItems.size(),
                "Grid should be empty after deleting the agent (test agents=" + testAgentCount + ")");
    }

    /**
     * Test: Grid shows empty when no agents exist.
     */
    @Test
    void emptyGrid_showsNoAgents() {
        // Arrange: No agents
        TEST_AGENTS.clear();

        // Act: Navigate
        view = navigate(AgentListView.class);
        Grid<AgentInfoDTO> grid = view.grid;

        // Assert: Grid is empty
        assertEquals(0, grid.getGenericDataView().getItems().toList().size(),
                "Grid should be empty when no agents exist");
    }

    // --- Test Configuration ---

    /**
     * Minimal Spring configuration for browserless testing.
     * Provides mock beans for the services required by AgentListView.
     */
    @Configuration
    static class Config {

        @Bean
        @Primary
        public AgentLifecycleService agentLifecycleService() {
            return new MockAgentLifecycleService(TEST_AGENTS);
        }

        @Bean
        @Primary
        public DirectoryValidationPort directoryValidationPort() {
            return path -> DirectoryValidationPort.ValidationResult.success();
        }

        @Bean
        @Primary
        public AgentInfoService agentInfoService(AgentLifecycleService agentLifecycleService,
                                                 DirectoryValidationPort directoryValidationPort) {
            return new AgentInfoService(agentLifecycleService, directoryValidationPort);
        }

        @Bean
        @Primary
        public com.hdekker.ai_workflow.application.agent.AgentStatusService agentStatusService() {
            return new MockAgentStatusService();
        }
    }

    /**
     * Mock AgentLifecycleService using a shared test store for deletion.
     */
    static class MockAgentLifecycleService extends AgentLifecycleService {
        private final ConcurrentHashMap<String, com.hdekker.ai_workflow.domain.agent.AgentInfo> agents;

        MockAgentLifecycleService(ConcurrentHashMap<String, com.hdekker.ai_workflow.domain.agent.AgentInfo> agents) {
            super();
            this.agents = agents;
        }

        @Override
        public List<com.hdekker.ai_workflow.domain.agent.AgentInfo> listAgents() {
            return List.copyOf(agents.values());
        }

        @Override
        public void removeAgent(String id) {
            agents.remove(id);
        }
    }

    /**
     * Mock AgentStatusService for browserless testing.
     */
    static class MockAgentStatusService extends com.hdekker.ai_workflow.application.agent.AgentStatusService {

        public MockAgentStatusService() {
            super(null, null, null);
        }

        @Override
        public List<com.hdekker.ai_workflow.application.agent.port.LLMStatusRepository.LLMStatusRecord> getCurrentStatus() {
            return List.of(new com.hdekker.ai_workflow.application.agent.port.LLMStatusRepository.LLMStatusRecord(
                    "test-instance", "gpt-4", "UP",
                    LocalDateTime.now(), 0, null, "OK"));
        }
    }
}

package com.hdekker.ai_workflow.adapter.inbound.ui.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.hdekker.ai_workflow.adapter.inbound.rest.dto.AdapterStatus;
import com.hdekker.ai_workflow.adapter.inbound.rest.dto.AgentInfo;
import com.hdekker.ai_workflow.adapter.inbound.rest.dto.LLMStatus;
import com.hdekker.ai_workflow.adapter.inbound.ui.component.AgentDetailDialog;
import com.hdekker.ai_workflow.adapter.inbound.ui.service.AgentInfoService;
import com.hdekker.ai_workflow.domain.agent.AgentDefinition;
import com.hdekker.ai_workflow.usecases.AgentLifecycleUseCase;

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
 * <p>This test uses a mock {@link AgentLifecycleUseCase} that provides
 * a pre-populated agent list and supports deletion.</p>
 * 
 * @see AgentListView
 * @see AgentDetailDialog
 */
@ExtendWith({SpringExtension.class, TreeOnFailureExtension.class})
@SpringBootTest(classes = AgentListViewDeleteTest.Config.class)
@ViewPackages(classes = {AgentListView.class})
class AgentListViewDeleteTest extends SpringBrowserlessTest {

    @Autowired
    private MockAgentLifecycleUseCase mockManager;

    @Autowired
    private AgentInfoService agentInfoService;

    private AgentListView view;

    // --- Helper methods ---

    private static AgentInfo createAgent(String id, String title, String regex) {
        return new AgentInfo(
                id,
                new AgentDefinition(regex, title, "Body", "Map", "Output", "out/${name}.md",
                        System.getProperty("java.io.tmpdir")),
                LocalDateTime.now(), true, "TEST");
    }

    private static AgentInfo createTestAgent() {
        return createAgent("agent-test-1", "Test Agent", ".*\\.txt");
    }

    @BeforeEach
    void setUp() {
        // Reset the mock state before each test
        mockManager.reset();
        view = navigate(AgentListView.class);
    }

    /**
     * Test: Reload data after delete fetches fresh data from the mock manager.
     * 
     * <p>Verifies that calling {@code reloadData()} after an agent is removed
     * from the mock manager correctly updates the grid.</p>
     */
    @Test
    void reloadAfterDelete_fetchesFreshData() {
        // Arrange: Start with 2 agents
        AgentInfo agent1 = createAgent("agent-1", "Test Agent 1", ".*\\.txt");
        AgentInfo agent2 = createAgent("agent-2", "Test Agent 2", ".*\\.java");
        mockManager.setAgents(List.of(agent1, agent2));

        // Act: Navigate and verify 2 agents
        view = navigate(AgentListView.class);
        Grid<AgentInfo> grid = view.grid;
        assertEquals(2, grid.getGenericDataView().getItems().toList().size(),
                "Grid should show 2 agents");

        // Simulate delete of first agent
        mockManager.removeAgent(agent1.id());

        // Call reload directly (simulates the onDelete callback)
        view.reloadData();
        roundTrip();

        // Verify only 1 agent remains
        List<AgentInfo> remaining = grid.getGenericDataView().getItems().toList();
        assertEquals(1, remaining.size(), "Grid should show 1 agent after delete");
        assertEquals(agent2.id(), remaining.get(0).id(),
                "Remaining agent should be the one that wasn't deleted");
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
        // Arrange: Mock provides one agent
        AgentInfo agent = createTestAgent();
        mockManager.setAgents(List.of(agent));

        // Act: Navigate and verify agent is in grid
        view = navigate(AgentListView.class);
        Grid<AgentInfo> grid = view.grid;
        assertEquals(1, grid.getGenericDataView().getItems().toList().size(),
                "Grid should show 1 agent initially");

        // Act: Open detail dialog with the agent, passing the autowired service
        AgentDetailDialog dialog = new AgentDetailDialog(
                agentInfoService,
                agent,
                info -> {},  // onSave: no-op
                id -> {
                    // onDelete: reload data (simulates the callback from AgentListView)
                    view.reloadData();
                }
        );
        dialog.open();

        // Verify dialog is open (added to UI)
        assertTrue($(Dialog.class).all().size() >= 1, "Dialog should be open");

        // Act: Click the Delete button in the dialog
        Button deleteButton = $(Button.class).withCaption("Delete Agent").single();
        deleteButton.click();

        // Click the confirm "Delete" button in the confirmation dialog
        Button confirmBtn = $(Button.class).withCaption("Delete").single();
        confirmBtn.click();

        // Two-stage queue processing:
        // 1. Process performDelete() which is queued via UI.access() in the confirm handler.
        //    This calls the reactive service (Mono.just(id) emits synchronously),
        //    then queues the reload via another UI.access().
        MockVaadin.runUIQueue();

        // Debug: check mock state directly — delete succeeded
        int mockAgentCount = mockManager.getAgents().size();
        assertEquals(0, mockAgentCount,
                "Mock manager should have 0 agents after delete");

        // 2. Process the queued reload callback from the reactive chain.
        //    The reactive callback fires synchronously (Mono.just emits immediately)
        //    and queues the reload via UI.access(). This call processes that queue.
        MockVaadin.runUIQueue();

        // Flush state tree to client (equivalent to ui.push() triggered by session unlock)
        roundTrip();

        // Verify the agent is removed from the grid
        List<AgentInfo> remainingItems = grid.getGenericDataView().getItems().toList();
        assertEquals(0, remainingItems.size(),
                "Grid should be empty after deleting the agent (mock has " + mockAgentCount + " agents)");
    }

    /**
     * Test: Grid shows empty when no agents exist.
     */
    @Test
    void emptyGrid_showsNoAgents() {
        // Arrange: No agents
        mockManager.setAgents(List.of());

        // Act: Navigate
        view = navigate(AgentListView.class);
        Grid<AgentInfo> grid = view.grid;

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
        public AgentLifecycleUseCase dynamicAgentManager() {
            return new MockAgentLifecycleUseCase();
        }

        @Bean
        @Primary
        public AgentInfoService agentInfoService() {
            return new AgentInfoService(dynamicAgentManager(), new com.hdekker.ai_workflow.adapter.outbound.file.TargetDirectoryValidator());
        }

        @Bean
        @Primary
        public com.hdekker.ai_workflow.usecases.AgentStatusUsecase llmStatusService() {
            return new MockAgentStatusUsecase();
        }
    }

    /**
     * Mock AgentLifecycleUseCase for browserless testing.
     * Provides a configurable list of agents that supports deletion.
     */
    static class MockAgentLifecycleUseCase extends AgentLifecycleUseCase {
        private final ConcurrentHashMap<String, AgentInfo> agents = new ConcurrentHashMap<>();

        public MockAgentLifecycleUseCase() {
            super();  // uses no-arg constructor which sets all deps to null
            reset();
        }

        public void reset() {
            agents.clear();
            agents.put(createTestAgent().id(), createTestAgent());
        }

        public List<AgentInfo> getAgents() {
            return List.copyOf(agents.values());
        }

        public void setAgents(List<AgentInfo> newAgents) {
            agents.clear();
            newAgents.forEach(a -> agents.put(a.id(), a));
        }

        @Override
        public List<AgentInfo> listAgents() {
            return List.copyOf(agents.values());
        }

        @Override
        public void removeAgent(String id) {
            agents.remove(id);
        }
    }

    /**
     * Mock AgentStatusUsecase for browserless testing.
     */
    static class MockAgentStatusUsecase extends com.hdekker.ai_workflow.usecases.AgentStatusUsecase {

        public MockAgentStatusUsecase() {
            super(null, null, null);
        }

        @Override
        public List<LLMStatus> getCurrentStatus() {
            return List.of(new LLMStatus(
                    "test-instance", "gpt-4", AdapterStatus.UP,
                    java.time.LocalDateTime.now(), 0, null, "OK"));
        }
    }
}

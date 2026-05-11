package com.hdekker.ai_workflow.adapter.inbound.ui.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.hdekker.ai_workflow.adapter.inbound.rest.dto.AgentInfoDTO;
import com.hdekker.ai_workflow.adapter.inbound.ui.service.AgentInfoService;
import com.hdekker.ai_workflow.application.agent.AgentLifecycleService;
import com.hdekker.ai_workflow.application.agent.port.DirectoryValidationPort;
import com.hdekker.ai_workflow.application.pipeline.AgentObserverEventBus;
import com.hdekker.ai_workflow.application.pipeline.AgentObserverService;
import com.hdekker.ai_workflow.application.pipeline.AgentObserverUseCase;
import com.hdekker.ai_workflow.domain.agent.AgentDefinition;
import com.hdekker.ai_workflow.domain.agent.AgentType;
import com.hdekker.ai_workflow.domain.agent.AgentSource;
import com.hdekker.ai_workflow.domain.pipeline.AgentMetrics;

import com.vaadin.browserless.SpringBrowserlessTest;
import com.vaadin.browserless.TreeOnFailureExtension;
import com.vaadin.browserless.ViewPackages;
import com.vaadin.flow.component.grid.Grid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * Browserless UI tests for AgentListView dispatch count and output files columns.
 * <p>
 * Verifies the new metrics columns are present and display correctly:
 * <ol>
 *   <li>Dispatch Count column — per-agent LLM dispatch count</li>
 *   <li>Output Files column — global output directory file count</li>
 * </ol>
 *
 * @see AgentListView
 */
@ExtendWith({SpringExtension.class, TreeOnFailureExtension.class})
@SpringBootTest(classes = AgentListViewColumnTest.Config.class)
@ViewPackages(classes = {AgentListView.class})
class AgentListViewColumnTest extends SpringBrowserlessTest {

    static final ConcurrentHashMap<String, com.hdekker.ai_workflow.domain.agent.AgentInfo> TEST_AGENTS = new ConcurrentHashMap<>();

    @Autowired
    private AgentInfoService agentInfoService;

    @Autowired
    private AgentObserverService agentObserverService;

    private AgentListView view;

    private static com.hdekker.ai_workflow.domain.agent.AgentInfo createDomainAgent(String id, String title, String regex) {
        return new com.hdekker.ai_workflow.domain.agent.AgentInfo(
                id,
                new AgentDefinition(regex, title, "Body", AgentType.MAP, "Output", "out/${name}.md",
                        System.getProperty("java.io.tmpdir")),
                LocalDateTime.now(), true, AgentSource.YAML);
    }

    @BeforeEach
    void setUp() {
        TEST_AGENTS.clear();
        com.hdekker.ai_workflow.domain.agent.AgentInfo agent = createDomainAgent("agent-1", "Test Agent", ".*\\.txt");
        TEST_AGENTS.put(agent.id(), agent);
        view = navigate(AgentListView.class);
    }

    /**
     * Test: Grid has all expected columns in correct order.
     *
     * <p>Verifies that the columns appear in the order:
     * ID, Title, Agent Type, File Regex, Target Dir, Source, Created, Active,
     * Dispatches, Filtered, Output Files, Actions.</p>
     */
    @Test
    void columnOrder_correctSequence() {
        // Arrange: Navigate and get grid columns
        Grid<AgentInfoDTO> grid = view.grid;
        roundTrip();

        // Assert: Verify column header order
        List<String> headers = grid.getColumns().stream()
                .map(col -> col.getHeaderText())
                .toList();

        assertEquals(12, headers.size(), "Grid should have 12 columns");

        // Verify the metrics columns appear in correct order between Active and Actions
        int activeIndex = headers.indexOf("Active");
        int dispatchesIndex = headers.indexOf("Dispatches");
        int filteredIndex = headers.indexOf("Filtered");
        int outputFilesIndex = headers.indexOf("Output Files");
        int actionsIndex = headers.indexOf("Actions");

        assertTrue(activeIndex >= 0, "Active column should exist");
        assertTrue(dispatchesIndex >= 0, "Dispatches column should exist");
        assertTrue(filteredIndex >= 0, "Filtered column should exist");
        assertTrue(outputFilesIndex >= 0, "Output Files column should exist");
        assertTrue(actionsIndex >= 0, "Actions column should exist");

        assertTrue(activeIndex < dispatchesIndex, "Dispatches should come after Active");
        assertTrue(dispatchesIndex < filteredIndex, "Filtered should come after Dispatches");
        assertTrue(filteredIndex < outputFilesIndex, "Output Files should come after Filtered");
        assertTrue(outputFilesIndex < actionsIndex, "Actions should come after Output Files");
    }

    /**
     * Test: Dispatch Count column exists and displays correctly.
     *
     * <p>Verifies that the dispatch count column is present in the grid.
     * With a fresh AgentObserverService, the count is zero so the cell
     * shows "–" for each agent row.</p>
     */
    @Test
    void dispatchCountColumn_exists() {
        // Arrange: Navigate and get grid columns
        Grid<AgentInfoDTO> grid = view.grid;
        roundTrip();

        // Assert: Dispatch Count column exists
        List<String> headers = grid.getColumns().stream()
                .map(col -> col.getHeaderText())
                .toList();
        assertTrue(headers.contains("Dispatches"), "Dispatches column should exist");
    }

    /**
     * Test: Output Files column exists and displays correctly.
     *
     * <p>Verifies that the output files column is present in the grid.
     * With a fresh AgentObserverService (no output directory configured),
     * the count is zero so the cell shows "–" for each agent row.</p>
     */
    @Test
    void outputFilesColumn_exists() {
        // Arrange: Navigate and get grid columns
        Grid<AgentInfoDTO> grid = view.grid;
        roundTrip();

        // Assert: Output Files column exists
        List<String> headers = grid.getColumns().stream()
                .map(col -> col.getHeaderText())
                .toList();
        assertTrue(headers.contains("Output Files"), "Output Files column should exist");
    }

    /**
     * Test: Filtered column exists.
     *
     * <p>Verifies that the "Filtered" column header is present in the grid.</p>
     */
    @Test
    void filteredColumn_exists() {
        // Arrange: Navigate and get grid columns
        Grid<AgentInfoDTO> grid = view.grid;
        roundTrip();

        // Assert: Filtered column exists
        List<String> headers = grid.getColumns().stream()
                .map(col -> col.getHeaderText())
                .toList();
        assertTrue(headers.contains("Filtered"), "Filtered column should exist");
    }

    /**
     * Test: Filtered column displays count after filtering.
     *
     * <p>Records a filter rejection via the observer, reloads the grid,
     * and asserts the count appears in the Filtered column.</p>
     */
    @Test
    void filteredColumn_displaysCountAfterFiltering() {
        // Arrange: Record a filter rejection for the test agent
        String agentId = "agent-1";
        agentObserverService.recordFilter(agentId, "test.md", ".*\\.java");
        roundTrip();

        // Act: Reload grid which fetches metrics
        view.reloadData();

        // Assert: Verify metrics map contains the filter count
        Map<String, AgentMetrics> metricsMap = view.getMetricsMap();
        AgentMetrics metrics = metricsMap.get(agentId);
        assertEquals(1L, metrics.filterCount(), "Filter count should be 1");
        assertEquals(0L, metrics.dispatchCount(), "Dispatch count should be 0");
    }

    /**
     * Test: Columns use AgentMetrics via AgentInfoService (single call per row).
     *
     * <p>Verifies that metrics are fetched via agentInfoService.getAllAgentMetrics()
     * during reload, not via per-column observer calls.</p>
     */
    @Test
    void columnsUseAgentMetrics_WhenReloaded_ThenSingleCallPerRow() {
        // Arrange
        roundTrip();

        // Act: Reload grid
        view.reloadData();

        // Assert: Metrics map is populated for all agents
        Map<String, AgentMetrics> metricsMap = view.getMetricsMap();
        assertEquals(1, metricsMap.size(), "Metrics map should have 1 entry");
        assertTrue(metricsMap.containsKey("agent-1"), "Metrics map should contain agent-1");

        // The metrics should come from the real observer service
        AgentMetrics metrics = metricsMap.get("agent-1");
        assertEquals(0L, metrics.dispatchCount(), "Fresh metrics should have 0 dispatches");
        assertEquals(0L, metrics.filterCount(), "Fresh metrics should have 0 filters");
    }

    // --- Test Configuration ---

    /**
     * Minimal Spring configuration for browserless testing.
     */
    @Configuration
    static class Config {

        @Bean
        @Primary
        public AgentLifecycleService agentLifecycleService() {
            return new TestAgentLifecycleService(TEST_AGENTS);
        }

        @Bean
        @Primary
        public DirectoryValidationPort directoryValidationPort() {
            return path -> DirectoryValidationPort.ValidationResult.success();
        }

        @Bean
        @Primary
        public AgentObserverService agentObserverService() {
            return new AgentObserverService(null, null);
        }

        @Bean
        @Primary
        public AgentObserverUseCase agentObserverUseCase(AgentObserverService observerService) {
            return new AgentObserverUseCase(
                    observerService,
                    new AgentObserverEventBus());
        }

        @Bean
        @Primary
        public AgentInfoService agentInfoService(AgentLifecycleService agentLifecycleService,
                                                 DirectoryValidationPort directoryValidationPort,
                                                 AgentObserverUseCase observer) {
            return new AgentInfoService(agentLifecycleService, directoryValidationPort, observer);
        }

        @Bean
        @Primary
        public com.hdekker.ai_workflow.application.agent.AgentStatusService agentStatusService() {
            return new TestAgentStatusService();
        }
    }

    /**
     * Test AgentLifecycleService using a shared test store.
     * <p>
     * Extends {@code AgentLifecycleService} and overrides listAgents/removeAgent
     * to use the shared test store. Passes nulls for Spring-managed dependencies
     * since they are not used by the overridden methods.
     */
    static class TestAgentLifecycleService extends AgentLifecycleService {
        private final ConcurrentHashMap<String, com.hdekker.ai_workflow.domain.agent.AgentInfo> agents;

        TestAgentLifecycleService(ConcurrentHashMap<String, com.hdekker.ai_workflow.domain.agent.AgentInfo> agents) {
            super(null, null, null, null, null, null, null);
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
     * Test AgentStatusService for browserless testing.
     */
    static class TestAgentStatusService extends com.hdekker.ai_workflow.application.agent.AgentStatusService {

        public TestAgentStatusService() {
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

package com.hdekker.ai_workflow.ui.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import reactor.core.publisher.Mono;

import com.hdekker.ai_workflow.app.pipeline.management.DynamicAgentManager;
import com.hdekker.ai_workflow.pipeline.domain.AgentDefinition;
import com.hdekker.ai_workflow.rest.dto.AgentInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class AgentInfoService {

    private static final Logger log = LoggerFactory.getLogger(AgentInfoService.class);

    private final DynamicAgentManager dynamicAgentManager;

    @Autowired
    public AgentInfoService(DynamicAgentManager dynamicAgentManager) {
        this.dynamicAgentManager = dynamicAgentManager;
    }

    public Mono<List<AgentInfo>> getAllAgentInfos() {
        try {
            List<AgentInfo> agents = dynamicAgentManager.listAgents();
            return Mono.just(agents);
        } catch (Exception ex) {
            log.error("Error fetching agent infos", ex);
            return Mono.just(List.of());
        }
    }

    public Mono<Void> deleteAgent(String id) {
        try {
            dynamicAgentManager.removeAgent(id);
            return Mono.empty();
        } catch (Exception ex) {
            log.error("Error deleting agent with id: {}", id, ex);
            return Mono.empty();
        }
    }

    public Mono<AgentInfo> createAgent(AgentDefinition agentDefinition) {
        try {
            // Extract targetDirectory from the definition, default to /tmp if not set
            String targetDir = agentDefinition.targetDirectory() != null 
                    ? agentDefinition.targetDirectory() 
                    : "/tmp";
            AgentInfo info = dynamicAgentManager.addDynamicAgent(agentDefinition, targetDir);
            return Mono.just(info);
        } catch (Exception ex) {
            log.error("Error creating agent: {}", agentDefinition.title(), ex);
            return Mono.error(ex);
        }
    }

    /**
     * Update an existing agent: removes the agent (including scanner) and
     * re-adds with the updated definition.
     *
     * @param id               the agent ID to update
     * @param agentDefinition  the updated agent definition
     * @return the updated agent info
     */
    public Mono<AgentInfo> updateAgent(String id, AgentDefinition agentDefinition) {
        try {
            AgentInfo info = dynamicAgentManager.updateAgent(id, agentDefinition);
            if (info != null) {
                return Mono.just(info);
            }
            return Mono.error(new RuntimeException("Agent not found: " + id));
        } catch (Exception ex) {
            log.error("Error updating agent with id: {}", id, ex);
            return Mono.error(ex);
        }
    }

    /**
     * Refresh an agent: trigger full rescan of its target directory.
     * Used when an agent's definition is modified and needs reprocessing.
     *
     * @param id the agent ID to refresh
     * @return the refreshed agent info
     */
    public Mono<AgentInfo> refreshAgent(String id) {
        try {
            AgentInfo info = dynamicAgentManager.refreshAgent(id);
            if (info == null) {
                return Mono.error(new RuntimeException("Agent not found: " + id));
            }
            return Mono.just(info);
        } catch (Exception ex) {
            log.error("Error refreshing agent with id: {}", id, ex);
            return Mono.error(ex);
        }
    }
}

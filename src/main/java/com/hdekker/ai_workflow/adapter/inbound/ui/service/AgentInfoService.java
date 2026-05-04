package com.hdekker.ai_workflow.adapter.inbound.ui.service;

import java.util.List;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hdekker.ai_workflow.adapter.inbound.rest.dto.AgentInfo;
import com.hdekker.ai_workflow.application.agent.AgentLifecycleService;
import com.hdekker.ai_workflow.application.agent.port.DirectoryValidationPort;
import com.hdekker.ai_workflow.application.agent.port.DirectoryValidationPort.ValidationResult;
import com.hdekker.ai_workflow.domain.agent.AgentDefinition;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class AgentInfoService {

    private static final Logger log = LoggerFactory.getLogger(AgentInfoService.class);

    private final AgentLifecycleService agentLifecycleService;
    private final DirectoryValidationPort directoryValidationPort;

    @Autowired
    public AgentInfoService(AgentLifecycleService agentLifecycleService,
                            DirectoryValidationPort directoryValidationPort) {
        this.agentLifecycleService = agentLifecycleService;
        this.directoryValidationPort = directoryValidationPort;
    }

    public Mono<List<AgentInfo>> getAllAgentInfos() {
        try {
            List<com.hdekker.ai_workflow.domain.agent.AgentInfo> domainAgents = agentLifecycleService.listAgents();
            List<AgentInfo> result = new java.util.ArrayList<>();
            for (com.hdekker.ai_workflow.domain.agent.AgentInfo d : domainAgents) {
                result.add(new AgentInfo(d.id(), d.definition(), d.createdAt(), d.active(), d.source()));
            }
            return Mono.just(result);
        } catch (Exception ex) {
            log.error("Error fetching agent infos", ex);
            return Mono.just(List.of());
        }
    }

    public Mono<String> deleteAgent(String id) {
        try {
            agentLifecycleService.removeAgent(id);
            return Mono.just(id);
        } catch (Exception ex) {
            log.error("Error deleting agent with id: {}", id, ex);
            return Mono.error(ex);
        }
    }

    public Mono<AgentInfo> createAgent(AgentDefinition agentDefinition) {
        try {
            String targetDir = agentDefinition.targetDirectory();
            ValidationResult result = directoryValidationPort.validate(targetDir);
            if (!result.valid()) {
                return Mono.error(new IllegalArgumentException(result.reason()));
            }
            com.hdekker.ai_workflow.domain.agent.AgentInfo domainInfo = agentLifecycleService.addDynamicAgent(agentDefinition, targetDir);
            return Mono.just(new AgentInfo(domainInfo.id(), domainInfo.definition(),
                    domainInfo.createdAt(), domainInfo.active(), domainInfo.source()));
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
            com.hdekker.ai_workflow.domain.agent.AgentInfo domainInfo = agentLifecycleService.updateAgent(id, agentDefinition);
            if (domainInfo != null) {
                return Mono.just(new AgentInfo(domainInfo.id(), domainInfo.definition(),
                        domainInfo.createdAt(), domainInfo.active(), domainInfo.source()));
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
            com.hdekker.ai_workflow.domain.agent.AgentInfo domainInfo = agentLifecycleService.refreshAgent(id);
            if (domainInfo == null) {
                return Mono.error(new RuntimeException("Agent not found: " + id));
            }
            return Mono.just(new AgentInfo(domainInfo.id(), domainInfo.definition(),
                    domainInfo.createdAt(), domainInfo.active(), domainInfo.source()));
        } catch (Exception ex) {
            log.error("Error refreshing agent with id: {}", id, ex);
            return Mono.error(ex);
        }
    }
}

package com.hdekker.ai_workflow.ui.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import reactor.core.publisher.Mono;

import com.hdekker.ai_workflow.app.pipeline.management.DynamicAgentManager;
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
}

package com.hdekker.ai_workflow.pipeline.management;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.hdekker.ai_workflow.usecases.AgentLifecycleUseCase;

/**
 * Restores persisted agents from the database when the application starts.
 * Ensures agents created via the UI or YAML config are available after restart.
 */
@Component
public class AgentRestoreOnStartup {

	private static final Logger log = LoggerFactory.getLogger(AgentRestoreOnStartup.class);

	private final AgentLifecycleUseCase dynamicAgentManager;

	public AgentRestoreOnStartup(AgentLifecycleUseCase dynamicAgentManager) {
		this.dynamicAgentManager = dynamicAgentManager;
	}

	@EventListener(ApplicationReadyEvent.class)
	public void restoreAgents() {
		log.info("Restoring agents from database on startup...");
		dynamicAgentManager.restoreFromDatabase();
	}
}

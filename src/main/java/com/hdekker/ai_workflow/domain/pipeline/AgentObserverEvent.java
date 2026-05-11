package com.hdekker.ai_workflow.domain.pipeline;

import java.time.LocalDateTime;

/**
 * Domain event representing an agent observer metric change.
 * <p>
 * Captures when a prompt is dispatched to the LLM, a response is stored
 * to the output directory, or a file is rejected by an input regex filter.
 * Used by {@link com.hdekker.ai_workflow.application.pipeline.port.AgentObserverEventPort}
 * for push notification to subscribers (UI, logging, dashboards).
 *
 * @param agentId     the owning agent's ID
 * @param eventType   the type of observer event
 * @param fileName    the file name associated with the event
 * @param regex       the regex that rejected the file (null for DISPATCHED/STORED)
 * @param timestamp   when the event occurred
 */
public record AgentObserverEvent(
        String agentId,
        AgentObserverEventType eventType,
        String fileName,
        String regex,
        LocalDateTime timestamp) {

    /**
     * Create a DISPATCHED event for the given agent and file.
     *
     * @param agentId  the owning agent's ID
     * @param fileName the file being dispatched
     * @return a new DISPATCHED event with the current timestamp
     */
    public static AgentObserverEvent dispatched(String agentId, String fileName) {
        return new AgentObserverEvent(agentId, AgentObserverEventType.DISPATCHED,
                fileName, null, LocalDateTime.now());
    }

    /**
     * Create a STORED event for the given agent and output.
     *
     * @param agentId   the owning agent's ID
     * @param outputName the output file name that was stored
     * @return a new STORED event with the current timestamp
     */
    public static AgentObserverEvent stored(String agentId, String outputName) {
        return new AgentObserverEvent(agentId, AgentObserverEventType.STORED,
                outputName, null, LocalDateTime.now());
    }

    /**
     * Create a FILTERED event for the given agent and file.
     *
     * @param agentId  the owning agent's ID
     * @param fileUrl  the file URL that was rejected
     * @param regex    the regex pattern that rejected the file (nullable)
     * @return a new FILTERED event with the current timestamp
     */
    public static AgentObserverEvent filtered(String agentId, String fileUrl, String regex) {
        return new AgentObserverEvent(agentId, AgentObserverEventType.FILTERED,
                fileUrl, regex, LocalDateTime.now());
    }
}

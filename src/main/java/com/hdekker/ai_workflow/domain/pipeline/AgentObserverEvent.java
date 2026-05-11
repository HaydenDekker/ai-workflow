package com.hdekker.ai_workflow.domain.pipeline;

import java.time.LocalDateTime;

/**
 * Domain event representing an agent observer metric change.
 * <p>
 * Captures when a prompt is dispatched to the LLM or a response is stored
 * to the output directory. Used by {@link AgentObserverEventPort} for push
 * notification to subscribers (UI, logging, dashboards).
 *
 * @param agentId     the owning agent's ID
 * @param eventType   the type of observer event (DISPATCHED or STORED)
 * @param fileName    the file name associated with the event (nullable for STORED)
 * @param timestamp   when the event occurred
 */
public record AgentObserverEvent(
        String agentId,
        AgentObserverEventType eventType,
        String fileName,
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
                fileName, LocalDateTime.now());
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
                outputName, LocalDateTime.now());
    }

    /**
     * Create a FILTERED event for the given agent and file.
     *
     * @param agentId  the owning agent's ID
     * @param fileUrl  the file URL that was rejected
     * @return a new FILTERED event with the current timestamp
     */
    public static AgentObserverEvent filtered(String agentId, String fileUrl) {
        return new AgentObserverEvent(agentId, AgentObserverEventType.FILTERED,
                fileUrl, LocalDateTime.now());
    }
}

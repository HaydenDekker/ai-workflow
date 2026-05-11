package com.hdekker.ai_workflow.domain.pipeline;

import java.time.LocalDateTime;

/**
 * Value object representing a file rejected by an agent's input regex filter.
 * <p>
 * Captures which agent rejected the file, the file URL, the regex pattern
 * that caused the rejection, and when it happened. Stored in a per-agent
 * ring buffer (capacity 10) inside {@link com.hdekker.ai_workflow.application.pipeline.AgentObserverService}.
 */
public record RegexFilterEntry(
        String agentId,
        String fileUrl,
        String regex,
        LocalDateTime timestamp) {

    /**
     * Create a filter entry for a rejected file.
     *
     * @param agentId the agent whose regex rejected the file
     * @param fileUrl the URL of the rejected file
     * @param regex   the regex pattern that rejected the file
     * @return a new entry with the current timestamp
     */
    public static RegexFilterEntry rejected(String agentId, String fileUrl, String regex) {
        return new RegexFilterEntry(agentId, fileUrl, regex, LocalDateTime.now());
    }
}

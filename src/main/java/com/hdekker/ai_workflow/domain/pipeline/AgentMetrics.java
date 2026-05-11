package com.hdekker.ai_workflow.domain.pipeline;

import java.util.Collections;
import java.util.List;

/**
 * Consolidated metrics snapshot for a single agent.
 * <p>
 * Combines dispatch count, filter count, and the last filtered entries
 * into one record so callers fetch everything in a single query instead
 * of multiple port calls.
 *
 * @param dispatchCount       number of prompts dispatched to the LLM
 * @param filterCount         number of files rejected by the input regex
 * @param lastFilteredEntries most recent rejected-file entries (max 10)
 */
public record AgentMetrics(
        long dispatchCount,
        long filterCount,
        List<RegexFilterEntry> lastFilteredEntries) {

    /**
     * Create an empty metrics snapshot with zero counts and no entries.
     *
     * @return an empty {@link AgentMetrics} instance
     */
    public static AgentMetrics empty() {
        return new AgentMetrics(0L, 0L, Collections.emptyList());
    }
}

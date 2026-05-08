package com.hdekker.ai_workflow.domain.pipeline;

/**
 * Types of agent observer events the pipeline can record.
 * <p>
 * {@code DISPATCHED} — a prompt was dispatched to the LLM adapter.
 * {@code STORED} — a response was persisted to the output directory.
 */
public enum AgentObserverEventType {

    /** The prompt was dispatched to the LLM for processing. */
    DISPATCHED,

    /** The response was stored to the output directory. */
    STORED
}

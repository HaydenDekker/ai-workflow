package com.hdekker.ai_workflow.adapter.inbound.rest.dto;

/**
 * Status states for LLM endpoints.
 */
public enum AdapterStatusDTO {
    UNKNOWN,     // No data yet (initial state)
    CONNECTING,  // Currently checking (transient state)
    UP,          // Healthy - green indicator
    WARN,        // Degraded - yellow indicator (last check > warnAfterHours)
    DOWN         // Unreachable - red indicator
}
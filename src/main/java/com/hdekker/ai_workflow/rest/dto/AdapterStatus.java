package com.hdekker.ai_workflow.rest.dto;

/**
 * Status states for LLM endpoints.
 */
public enum AdapterStatus {
    UNKNOWN,     // No data yet (initial state)
    CONNECTING,  // Currently checking (transient state)
    UP,          // Healthy - green indicator
    WARN,        // Degraded - yellow indicator (last check > warnAfterHours)
    DOWN         // Unreachable - red indicator
}
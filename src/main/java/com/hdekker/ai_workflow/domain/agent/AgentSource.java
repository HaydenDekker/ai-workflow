package com.hdekker.ai_workflow.domain.agent;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Domain enum representing the source of an agent's configuration.
 *
 * <p>Two possible sources:</p>
 * <ul>
 *   <li>{@code YAML} — agent loaded from a workflow YAML file</li>
 *   <li>{@code DYNAMIC} — agent created at runtime via the REST API</li>
 * </ul>
 */
public enum AgentSource {

    YAML("YAML"),
    DYNAMIC("DYNAMIC");

    private final String stringValue;

    AgentSource(String stringValue) {
        this.stringValue = stringValue;
    }

    /**
     * Returns the string value used for serialization.
     *
     * @return the string representation (e.g. "YAML", "DYNAMIC")
     */
    @JsonValue
    public String getAsString() {
        return stringValue;
    }

    /**
     * Parses a string value into the corresponding {@code AgentSource}.
     *
     * @param value the string value ("YAML" or "DYNAMIC")
     * @return the corresponding {@code AgentSource}
     * @throws IllegalArgumentException if the value is not recognized
     */
    @JsonCreator
    public static AgentSource fromString(String value) {
        if (value == null) {
            throw new IllegalArgumentException("AgentSource value must not be null");
        }
        for (AgentSource source : values()) {
            if (source.stringValue.equals(value)) {
                return source;
            }
        }
        throw new IllegalArgumentException(
                "Unknown agentSource: \"" + value + "\". Expected one of: YAML, DYNAMIC");
    }
}

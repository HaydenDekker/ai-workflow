package com.hdekker.ai_workflow.domain.agent;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Domain enum representing the type of agent processing strategy.
 *
 * <p>Maps to YAML values used in workflow files:</p>
 * <ul>
 *   <li>{@code MAP}       → "Map"       — one-to-one input/output transformation</li>
 *   <li>{@code REDUCTION} → "Reduction" — accumulates state across inputs</li>
 *   <li>{@code SPLIT}     → "Split"     — one-to-many output from single input</li>
 * </ul>
 *
 * <p>Serialization uses {@link #getAsString()} ({@code @JsonValue}) so that
 * Jackson produces YAML-compatible strings ("Map", "Reduction", "Split")
 * rather than enum constant names ("MAP", "REDUCTION", "SPLIT").</p>
 */
public enum AgentType {

    MAP("Map"),
    REDUCTION("Reduction"),
    SPLIT("Split");

    private final String yamlValue;

    AgentType(String yamlValue) {
        this.yamlValue = yamlValue;
    }

    /**
     * Returns the YAML-compatible string representation.
     *
     * <p>Used by Jackson ({@code @JsonValue}) for serialization so that
     * JSON/YAML output uses title-case values like "Map" instead of "MAP".</p>
     *
     * @return the YAML-compatible string (e.g. "Map", "Reduction", "Split")
     */
    @JsonValue
    public String getAsString() {
        return yamlValue;
    }

    /**
     * Parses a YAML string value into the corresponding {@code AgentType}.
     *
     * <p>Used by Jackson ({@code @JsonCreator}) for deserialization.
     * Handles {@code null}, empty, and blank strings by returning {@link #MAP}
     * (the default/fallback agent type). Unknown values throw
     * {@link IllegalArgumentException}.</p>
     *
     * @param value the YAML string ("Map", "Reduction", "Split", or {@code null})
     * @return the corresponding {@code AgentType}
     * @throws IllegalArgumentException if the value is non-blank but not recognized
     */
    @JsonCreator
    public static AgentType fromString(String value) {
        if (value == null || value.isBlank()) {
            return MAP;
        }
        for (AgentType type : values()) {
            if (type.yamlValue.equals(value)) {
                return type;
            }
        }
        // Handle legacy alias "Reduce" → REDUCTION
        if ("Reduce".equalsIgnoreCase(value)) {
            return REDUCTION;
        }
        throw new IllegalArgumentException(
                "Unknown agentType: \"" + value + "\". Expected one of: "
                        + java.util.Arrays.stream(values())
                                .map(AgentType::getAsString)
                                .toArray());
    }
}

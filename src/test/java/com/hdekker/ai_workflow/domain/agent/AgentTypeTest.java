package com.hdekker.ai_workflow.domain.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link AgentType} enum.
 *
 * Verifies enum values, {@code fromString()} parsing of YAML values,
 * {@code getAsString()} round-trip, and unknown value handling.
 */
class AgentTypeTest {

    // -- Enum values --

    @Test
    void shouldHaveThreeEnumValues() {
        assertThat(AgentType.values()).hasSize(3);
        assertThat(AgentType.values()).containsExactly(AgentType.MAP, AgentType.REDUCTION, AgentType.SPLIT);
    }

    // -- fromString() with valid YAML values --

    @Test
    void fromStringShouldParseMap() {
        assertThat(AgentType.fromString("Map")).isEqualTo(AgentType.MAP);
    }

    @Test
    void fromStringShouldParseReduction() {
        assertThat(AgentType.fromString("Reduction")).isEqualTo(AgentType.REDUCTION);
    }

    @Test
    void fromStringShouldParseSplit() {
        assertThat(AgentType.fromString("Split")).isEqualTo(AgentType.SPLIT);
    }

    // -- fromString() with null / blank --

    @Test
    void fromStringShouldReturnMapForNull() {
        assertThat(AgentType.fromString(null)).isEqualTo(AgentType.MAP);
    }

    @Test
    void fromStringShouldReturnMapForEmptyString() {
        assertThat(AgentType.fromString("")).isEqualTo(AgentType.MAP);
    }

    @Test
    void fromStringShouldReturnMapForBlankString() {
        assertThat(AgentType.fromString("   ")).isEqualTo(AgentType.MAP);
    }

    // -- fromString() with unknown values --

    @Test
    void fromStringShouldThrowForUnknownValue() {
        assertThatThrownBy(() -> AgentType.fromString("Unknown"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown");
    }

    @Test
    void fromStringShouldThrowForUnknownType() {
        assertThatThrownBy(() -> AgentType.fromString("Reducer"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Reducer");
    }

    // -- getAsString() round-trip --

    @Test
    void getAsStringShouldReturnYamlCompatibleValue() {
        assertThat(AgentType.MAP.getAsString()).isEqualTo("Map");
        assertThat(AgentType.REDUCTION.getAsString()).isEqualTo("Reduction");
        assertThat(AgentType.SPLIT.getAsString()).isEqualTo("Split");
    }

    @Test
    void fromStringAndGetAsStringShouldRoundTrip() {
        for (AgentType type : AgentType.values()) {
            String yamlValue = type.getAsString();
            AgentType parsed = AgentType.fromString(yamlValue);
            assertThat(parsed).isEqualTo(type);
        }
    }
}

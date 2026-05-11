package com.hdekker.ai_workflow.domain.agent;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link AgentSource} enum.
 */
class AgentSourceTest {

    @Test
    void values_ShouldReturnYamlAndDynamic() {
        AgentSource[] values = AgentSource.values();
        assertThat(values).hasSize(2);
        assertThat(values[0]).isEqualTo(AgentSource.YAML);
        assertThat(values[1]).isEqualTo(AgentSource.DYNAMIC);
    }

    @Test
    void fromString_YAML_ShouldReturnYAML() {
        assertThat(AgentSource.fromString("YAML")).isEqualTo(AgentSource.YAML);
    }

    @Test
    void fromString_DYNAMIC_ShouldReturnDYNAMIC() {
        assertThat(AgentSource.fromString("DYNAMIC")).isEqualTo(AgentSource.DYNAMIC);
    }

    @Test
    void fromString_Null_ShouldThrow() {
        assertThatThrownBy(() -> AgentSource.fromString(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("null");
    }

    @Test
    void fromString_Unknown_ShouldThrow() {
        assertThatThrownBy(() -> AgentSource.fromString("UNKNOWN"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("UNKNOWN");
    }

    @Test
    void getAsString_YAML_ShouldReturnYAML() {
        assertThat(AgentSource.YAML.getAsString()).isEqualTo("YAML");
    }

    @Test
    void getAsString_DYNAMIC_ShouldReturnDYNAMIC() {
        assertThat(AgentSource.DYNAMIC.getAsString()).isEqualTo("DYNAMIC");
    }

    @Test
    void fromString_GetAsString_RoundTrip() {
        for (AgentSource source : AgentSource.values()) {
            assertThat(AgentSource.fromString(source.getAsString())).isEqualTo(source);
        }
    }
}

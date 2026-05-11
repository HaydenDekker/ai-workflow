package com.hdekker.ai_workflow.domain.agent;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link AgentDefinition} constructor validation.
 */
class AgentDefinitionTest {

    private static final String VALID_REGEX = ".*\\\\.java";
    private static final String VALID_TITLE = "Test Agent";
    private static final String VALID_BODY = "Prompt body";
    private static final String VALID_STRUCTURE = "output";
    private static final String VALID_TEMPLATE = "output/${filename}.md";
    private static final String VALID_TARGET = "/tmp/target";

    @Test
    void constructWithAllValidFields_ShouldSucceed() {
        AgentDefinition def = new AgentDefinition(VALID_REGEX, VALID_TITLE, VALID_BODY, AgentType.MAP, VALID_STRUCTURE, VALID_TEMPLATE, VALID_TARGET);

        assertThat(def.fileInputRegex()).isEqualTo(VALID_REGEX);
        assertThat(def.title()).isEqualTo(VALID_TITLE);
        assertThat(def.body()).isEqualTo(VALID_BODY);
        assertThat(def.agentType()).isEqualTo(AgentType.MAP);
        assertThat(def.outputStructure()).isEqualTo(VALID_STRUCTURE);
        assertThat(def.outputFilenameTemplate()).isEqualTo(VALID_TEMPLATE);
        assertThat(def.targetDirectory()).isEqualTo(VALID_TARGET);
    }

    @Test
    void constructWithNullTitle_ShouldThrow() {
        assertThatThrownBy(() -> new AgentDefinition(VALID_REGEX, null, VALID_BODY, AgentType.MAP, VALID_STRUCTURE, VALID_TEMPLATE, VALID_TARGET))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("title");
    }

    @Test
    void constructWithNullBody_ShouldThrow() {
        assertThatThrownBy(() -> new AgentDefinition(VALID_REGEX, VALID_TITLE, null, AgentType.MAP, VALID_STRUCTURE, VALID_TEMPLATE, VALID_TARGET))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("body");
    }

    @Test
    void constructWithNullFileInputRegex_ShouldThrow() {
        assertThatThrownBy(() -> new AgentDefinition(null, VALID_TITLE, VALID_BODY, AgentType.MAP, VALID_STRUCTURE, VALID_TEMPLATE, VALID_TARGET))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("fileInputRegex");
    }

    @Test
    void constructWithNullAgentType_ShouldThrow() {
        assertThatThrownBy(() -> new AgentDefinition(VALID_REGEX, VALID_TITLE, VALID_BODY, null, VALID_STRUCTURE, VALID_TEMPLATE, VALID_TARGET))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("agentType");
    }

    @Test
    void constructWithInvalidRegex_ShouldThrow() {
        assertThatThrownBy(() -> new AgentDefinition("[invalid", VALID_TITLE, VALID_BODY, AgentType.MAP, VALID_STRUCTURE, VALID_TEMPLATE, VALID_TARGET))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalid");
    }

    @Test
    void constructWithEmptyRegex_ShouldThrow() {
        assertThatThrownBy(() -> new AgentDefinition("", VALID_TITLE, VALID_BODY, AgentType.MAP, VALID_STRUCTURE, VALID_TEMPLATE, VALID_TARGET))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void constructWithEmptyTitle_ShouldSucceed() {
        // Empty title is allowed (just not null)
        AgentDefinition def = new AgentDefinition(VALID_REGEX, "", VALID_BODY, AgentType.MAP, VALID_STRUCTURE, VALID_TEMPLATE, VALID_TARGET);
        assertThat(def.title()).isEmpty();
    }

    @Test
    void constructWithEmptyBody_ShouldSucceed() {
        AgentDefinition def = new AgentDefinition(VALID_REGEX, VALID_TITLE, "", AgentType.MAP, VALID_STRUCTURE, VALID_TEMPLATE, VALID_TARGET);
        assertThat(def.body()).isEmpty();
    }

    @Test
    void inputRegexMatches_ValidRegex_ShouldReturnCorrectResult() {
        AgentDefinition def = new AgentDefinition(".*\\.java", VALID_TITLE, VALID_BODY, AgentType.MAP, VALID_STRUCTURE, VALID_TEMPLATE, VALID_TARGET);

        assertThat(def.inputRegexMatches("path/to/file.java")).isTrue();
        assertThat(def.inputRegexMatches("path/to/file.txt")).isFalse();
    }

    @Test
    void constructWithNullOutputStructure_ShouldSucceed() {
        // Null outputStructure is allowed
        AgentDefinition def = new AgentDefinition(VALID_REGEX, VALID_TITLE, VALID_BODY, AgentType.MAP, null, VALID_TEMPLATE, VALID_TARGET);
        assertThat(def.outputStructure()).isNull();
    }

    @Test
    void constructWithNullTargetDirectory_ShouldSucceed() {
        AgentDefinition def = new AgentDefinition(VALID_REGEX, VALID_TITLE, VALID_BODY, AgentType.MAP, VALID_STRUCTURE, VALID_TEMPLATE, null);
        assertThat(def.targetDirectory()).isNull();
    }
}

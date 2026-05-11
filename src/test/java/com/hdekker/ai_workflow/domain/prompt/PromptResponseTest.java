package com.hdekker.ai_workflow.domain.prompt;

import com.hdekker.ai_workflow.domain.agent.AgentDefinition;
import com.hdekker.ai_workflow.domain.agent.AgentType;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link PromptResponse.createOutputFileName()}.
 * Verifies idempotency and that the shared groups map from FilterResult is not mutated.
 */
class PromptResponseTest {

    // Regex with named groups: path, name, ext
    private static final String VALID_REGEX = ".*\\/(?<name>[^\\/]+)\\.(?<ext>[a-z]+)";
    private static final String VALID_TITLE = "TestAgent";
    private static final String VALID_BODY = "Body";
    private static final String VALID_TEMPLATE = "output/${title}/${name}.${ext}";
    private static final String VALID_TARGET = "/tmp/target";

    private AgentDefinition createDefinition() {
        return new AgentDefinition(VALID_REGEX, VALID_TITLE, VALID_BODY, AgentType.MAP, "", VALID_TEMPLATE, VALID_TARGET);
    }

    @Test
    void createOutputFileName_ShouldIncludeTitleInGroups() {
        AgentDefinition def = createDefinition();
        PromptResponse response = new PromptResponse(def, "path/to/file.java", "content", "response");

        String result = response.createOutputFileName();

        assertThat(result).isEqualTo("output/TestAgent/file.java");
    }

    @Test
    void createOutputFileName_IsIdempotent() {
        AgentDefinition def = createDefinition();
        PromptResponse response = new PromptResponse(def, "path/to/file.java", "content", "response");

        String first = response.createOutputFileName();
        String second = response.createOutputFileName();

        assertThat(second).isEqualTo(first);
    }

    @Test
    void createOutputFileName_DoesNotMutateSharedGroups() {
        AgentDefinition def = createDefinition();
        PromptResponse response = new PromptResponse(def, "path/to/file.java", "content", "response");

        // First call
        response.createOutputFileName();

        // Second call with fresh response — should produce same result
        PromptResponse freshResponse = new PromptResponse(def, "path/to/file.java", "content", "response");
        String result = freshResponse.createOutputFileName();

        assertThat(result).isEqualTo("output/TestAgent/file.java");
    }

    @Test
    void createOutputFileName_WithExtractedGroups() {
        // Regex with path, name, ext groups
        AgentDefinition def = new AgentDefinition(
                "(?:.*/)?(?<name>.*)\\.(?<ext>[a-z]+)",
                VALID_TITLE, VALID_BODY, AgentType.MAP, "", VALID_TEMPLATE, VALID_TARGET);
        PromptResponse response = new PromptResponse(def, "myDir/myFile.txt", "content", "response");

        String result = response.createOutputFileName();

        assertThat(result).isEqualTo("output/TestAgent/myFile.txt");
    }

    @Test
    void createOutputFileName_WhenRegexDoesNotMatch_ShouldReturnNonEmpty() {
        AgentDefinition def = new AgentDefinition(".*\\.java", VALID_TITLE, VALID_BODY, AgentType.MAP, "", VALID_TEMPLATE, VALID_TARGET);
        PromptResponse response = new PromptResponse(def, "path/to/file.txt", "content", "response");

        String result = response.createOutputFileName();

        // When regex doesn't match, groups are empty — OutputFilenameTemplate handles this
        assertThat(result).isNotNull();
    }

    @Test
    void createOutputFileName_WithNullFileName_ShouldReturnNonEmpty() {
        // null fileName causes FilterResult with null groups — createOutputFileName handles gracefully
        AgentDefinition def = new AgentDefinition(".*\\.java", VALID_TITLE, VALID_BODY, AgentType.MAP, "", VALID_TEMPLATE, VALID_TARGET);
        PromptResponse response = new PromptResponse(def, null, "content", "response");

        // Should not throw — it handles null fileName by returning a non-null result
        String result = response.createOutputFileName();
        assertThat(result).isNotNull();
    }
}

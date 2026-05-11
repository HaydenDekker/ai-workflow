package com.hdekker.ai_workflow.domain.shared;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link RegexInputFileFilter.FilterResult} immutability.
 */
class FilterResultTest {

    @Test
    void groups_ShouldBeImmutable() {
        RegexInputFileFilter.FilterResult result = RegexInputFileFilter.matches("path/to/file.java", ".*\\.java");

        assertThat(result.matches()).isTrue();
        assertThat(result.groups()).isNotNull();

        // Attempting to mutate should throw UnsupportedOperationException
        assertThatThrownBy(() -> result.groups().put("extra", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void groups_ShouldBeImmutable_ForNonMatching() {
        RegexInputFileFilter.FilterResult result = RegexInputFileFilter.matches("path/to/file.txt", ".*\\.java");

        assertThat(result.matches()).isFalse();
        assertThat(result.groups()).isNotNull();
        assertThat(result.groups()).isEmpty();

        // Attempting to mutate should throw UnsupportedOperationException
        assertThatThrownBy(() -> result.groups().put("extra", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void groups_ShouldContainExtractedGroups() {
        RegexInputFileFilter.FilterResult result = RegexInputFileFilter.matches(
                "logs/app.log", "(?<path>.*/)(?<name>.*)\\.(?<ext>.*)");

        assertThat(result.matches()).isTrue();
        assertThat(result.groups()).containsEntry("path", "logs/");
        assertThat(result.groups()).containsEntry("name", "app");
        assertThat(result.groups()).containsEntry("ext", "log");

        // Still immutable
        assertThatThrownBy(() -> result.groups().put("extra", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void path_Name_Ext_Accessors_WithNullGroups_ShouldReturnEmpty() {
        // When regex is null, groups is null
        RegexInputFileFilter.FilterResult result = RegexInputFileFilter.matches("file.txt", null);

        assertThat(result.matches()).isFalse();
        assertThat(result.path()).isEmpty();
        assertThat(result.name()).isEmpty();
        assertThat(result.ext()).isEmpty();
    }
}

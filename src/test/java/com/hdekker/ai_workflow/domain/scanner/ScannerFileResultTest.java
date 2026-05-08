package com.hdekker.ai_workflow.domain.scanner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ScannerFileResult}.
 * <p>
 * Validates the domain enum values and the {@code from(ScannerEventType)} factory method
 * that maps scanner event types to file-level result outcomes.
 */
class ScannerFileResultTest {

    @Test
    void givenEnumValues_ThenContainsExpectedEntries() {
        ScannerFileResult[] values = ScannerFileResult.values();

        assertThat(values).hasSize(3);
        assertThat(values).containsExactly(
                ScannerFileResult.EMITTED,
                ScannerFileResult.FILTERED,
                ScannerFileResult.ERROR
        );
    }

    @Test
    void givenCreationEvent_ThenMapsToEmitted() {
        assertThat(ScannerFileResult.from(ScannerEventType.CREATION))
                .isEqualTo(ScannerFileResult.EMITTED);
    }

    @Test
    void givenModificationEvent_ThenMapsToEmitted() {
        assertThat(ScannerFileResult.from(ScannerEventType.MODIFICATION))
                .isEqualTo(ScannerFileResult.EMITTED);
    }

    @Test
    void givenDeletionEvent_ThenMapsToEmitted() {
        assertThat(ScannerFileResult.from(ScannerEventType.DELETION))
                .isEqualTo(ScannerFileResult.EMITTED);
    }

    @Test
    void givenUnchangedEvent_ThenMapsToFiltered() {
        assertThat(ScannerFileResult.from(ScannerEventType.UNCHANGED))
                .isEqualTo(ScannerFileResult.FILTERED);
    }

    @Test
    void givenNullEventType_ThenMapsToEmitted() {
        assertThat(ScannerFileResult.from(null))
                .isEqualTo(ScannerFileResult.EMITTED);
    }

    @Test
    void givenFromValueWithSameName_ThenReturnsMatchingResult() {
        assertThat(ScannerFileResult.fromValue("EMITTED"))
                .isEqualTo(ScannerFileResult.EMITTED);
        assertThat(ScannerFileResult.fromValue("FILTERED"))
                .isEqualTo(ScannerFileResult.FILTERED);
        assertThat(ScannerFileResult.fromValue("ERROR"))
                .isEqualTo(ScannerFileResult.ERROR);
    }

    @Test
    void givenFromValueWithLowerCase_ThenReturnsMatchingResult() {
        assertThat(ScannerFileResult.fromValue("emitted"))
                .isEqualTo(ScannerFileResult.EMITTED);
        assertThat(ScannerFileResult.fromValue("filtered"))
                .isEqualTo(ScannerFileResult.FILTERED);
        assertThat(ScannerFileResult.fromValue("error"))
                .isEqualTo(ScannerFileResult.ERROR);
    }

    @Test
    void givenFromValueWithUnknownName_ThenThrows() {
        assertThatThrownBy(() -> ScannerFileResult.fromValue("UNKNOWN"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void givenFromValueWithNull_ThenThrows() {
        assertThatThrownBy(() -> ScannerFileResult.fromValue(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void givenFromValueWithBlank_ThenThrows() {
        assertThatThrownBy(() -> ScannerFileResult.fromValue("  "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

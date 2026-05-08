package com.hdekker.ai_workflow.adapter.inbound.rest.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ScannerInfoDTO} constructor and field access.
 */
class ScannerInfoDTOConstructorTest {

    private static final String ID = "scanner-1";
    private static final String AGENT_ID = "agent-1";
    private static final String FOLDER = "/data/scans";
    private static final String STATUS = "EMITTING_UPDATES";
    private static final String FILE_RESULT = "EMITTED";
    private static final String ERROR_MSG = "IO error";
    private static final LocalDateTime CREATED = LocalDateTime.of(2026, 5, 8, 10, 0);
    private static final LocalDateTime EMITTED_AT = LocalDateTime.of(2026, 5, 8, 10, 5);

    @Test
    void givenAllFields_ExpectAllPopulated() {
        ScannerInfoDTO dto = new ScannerInfoDTO(
                ID, AGENT_ID, FOLDER, STATUS, CREATED, EMITTED_AT, ERROR_MSG, 42L, FILE_RESULT
        );

        assertThat(dto.id()).isEqualTo(ID);
        assertThat(dto.agentId()).isEqualTo(AGENT_ID);
        assertThat(dto.targetDirectory()).isEqualTo(FOLDER);
        assertThat(dto.status()).isEqualTo(STATUS);
        assertThat(dto.createdAt()).isEqualTo(CREATED);
        assertThat(dto.lastEmittedAt()).isEqualTo(EMITTED_AT);
        assertThat(dto.errorMessage()).isEqualTo(ERROR_MSG);
        assertThat(dto.fileCount()).isEqualTo(42L);
        assertThat(dto.fileResult()).isEqualTo(FILE_RESULT);
    }

    @Test
    void givenEmptyErrorMessage_ExpectNormalizedToBlank() {
        ScannerInfoDTO dto = new ScannerInfoDTO(
                ID, AGENT_ID, FOLDER, STATUS, CREATED, EMITTED_AT, null, 0L, "FILTERED"
        );

        assertThat(dto.errorMessage()).isEqualTo("");
    }

    @Test
    void givenMinimalFields_ExpectDefaultsApplied() {
        ScannerInfoDTO dto = new ScannerInfoDTO(
                ID, AGENT_ID, FOLDER, STATUS, CREATED, EMITTED_AT
        );

        assertThat(dto.id()).isEqualTo(ID);
        assertThat(dto.agentId()).isEqualTo(AGENT_ID);
        assertThat(dto.targetDirectory()).isEqualTo(FOLDER);
        assertThat(dto.status()).isEqualTo(STATUS);
        assertThat(dto.createdAt()).isEqualTo(CREATED);
        assertThat(dto.lastEmittedAt()).isEqualTo(EMITTED_AT);
        assertThat(dto.errorMessage()).isEqualTo("");
        assertThat(dto.fileCount()).isEqualTo(0L);
    }
}

package com.hdekker.ai_workflow.adapter.outbound.persistence.scanner;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;


import org.junit.jupiter.api.Test;

public class ScannerEntityTest {

    @Test
    public void givenNewEntity_ExpectDefaultValues() {
        ScannerEntity entity = new ScannerEntity();

        assertThat(entity.getId()).isNull();
        assertThat(entity.getTargetDirectory()).isNull();
        assertThat(entity.getStatus()).isEqualTo("IDLE");
        assertThat(entity.getCreatedAt()).isNull();
        assertThat(entity.getLastEmittedAt()).isNull();
    }

    @Test
    public void givenEntityWithValues_ExpectAllFieldsSettable() {
        ScannerEntity entity = new ScannerEntity();
        String id = "test-scanner-id";
        String dir = "/test/target";
        String status = "EMITTING_UPDATES";
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime lastEmitted = now.minusMinutes(5);

        entity.setId(id);
        entity.setTargetDirectory(dir);
        entity.setStatus(status);
        entity.setCreatedAt(now);
        entity.setLastEmittedAt(lastEmitted);

        assertThat(entity.getId()).isEqualTo(id);
        assertThat(entity.getTargetDirectory()).isEqualTo(dir);
        assertThat(entity.getStatus()).isEqualTo(status);
        assertThat(entity.getCreatedAt()).isEqualTo(now);
        assertThat(entity.getLastEmittedAt()).isEqualTo(lastEmitted);
    }

    @Test
    public void givenEntityWithStatusUpdate_ExpectStatusChanged() {
        ScannerEntity entity = new ScannerEntity();
        entity.setStatus("IDLE");

        entity.setStatus("ERROR");
        assertThat(entity.getStatus()).isEqualTo("ERROR");
    }

    @Test
    public void givenEntityWithTargetDirectory_ExpectPathStored() {
        ScannerEntity entity = new ScannerEntity();
        entity.setTargetDirectory("C:\\data\\uploads\\contracts");

        assertThat(entity.getTargetDirectory()).isEqualTo("C:\\data\\uploads\\contracts");
    }
}

package com.hdekker.ai_workflow.application.agent;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.hdekker.ai_workflow.application.agent.port.LLMHealthPort;
import com.hdekker.ai_workflow.application.agent.port.LLMStatusRepository;
import com.hdekker.ai_workflow.config.ObservabilityProperties;

import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

/**
 * Unit tests for AgentStatusService.
 *
 * Tests cover:
 * - Scheduled polling (success, failure, null endpoint)
 * - WARN condition detection (stale data, fresh data, already DOWN)
 * - Manual trigger
 * - Status retrieval
 */
@ExtendWith(MockitoExtension.class)
class AgentStatusServiceTest {

    @Mock
    private LLMStatusRepository repository;

    @Mock
    private LLMHealthPort healthPort;

    @Mock
    private ObservabilityProperties observabilityProperties;

    @Captor
    private ArgumentCaptor<String> endpointCaptor;

    private AgentStatusService service;

    @BeforeEach
    void setUp() {
        service = new AgentStatusService(repository, healthPort, observabilityProperties);
    }

    /**
     * Helper to set private fields via reflection (e.g. @Value fields).
     */
    private void setServiceField(String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = AgentStatusService.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(service, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set field " + fieldName, e);
        }
    }

    // ==================== schedulePolling() tests ====================

    @Test
    void schedulePolling_success_persistsStatus() {
        // Arrange
        when(observabilityProperties.getEndpoint()).thenReturn("http://localhost:8080");
        when(observabilityProperties.getModel()).thenReturn("test-model");

        LLMHealthPort.LLMStatus healthStatus = new LLMHealthPort.LLMStatus(
            "http://localhost:8080", "test-model", LLMHealthPort.LLMStatus.HealthStatus.UP,
            LocalDateTime.now(), 3, List.of("model1", "model2", "model3"), null
        );
        when(healthPort.checkHealth(any(), any())).thenReturn(Mono.just(healthStatus));

        // Act
        service.schedulePolling();

        // Assert
        verify(repository, times(1)).save(
            eq("http://localhost:8080"), eq("test-model"), eq("UP"),
            any(LocalDateTime.class), eq(3), eq("model1,model2,model3"), isNull()
        );
    }

    @Test
    void schedulePolling_failure_persistsDownStatus() {
        // Arrange
        when(observabilityProperties.getEndpoint()).thenReturn("http://localhost:8080");
        when(observabilityProperties.getModel()).thenReturn("test-model");

        LLMHealthPort.LLMStatus healthStatus = new LLMHealthPort.LLMStatus(
            "http://localhost:8080", "test-model", LLMHealthPort.LLMStatus.HealthStatus.DOWN,
            LocalDateTime.now(), 0, List.of(), "Connection refused"
        );
        when(healthPort.checkHealth(any(), any())).thenReturn(Mono.just(healthStatus));

        // Act
        service.schedulePolling();

        // Assert
        verify(repository, times(1)).save(
            eq("http://localhost:8080"), eq("test-model"), eq("DOWN"),
            any(LocalDateTime.class), eq(0), eq(""), eq("Connection refused")
        );
    }

    @Test
    void schedulePolling_nullEndpoint_skipsHealthCheck() {
        // Arrange
        when(observabilityProperties.getEndpoint()).thenReturn(null);

        // Act
        service.schedulePolling();

        // Assert
        verify(healthPort, never()).checkHealth(any(), any());
        verify(repository, never()).save(any(), any(), any(), any(), anyInt(), any(), any());
    }

    @Test
    void schedulePolling_emptyEndpoint_skipsHealthCheck() {
        // Arrange
        when(observabilityProperties.getEndpoint()).thenReturn("");

        // Act
        service.schedulePolling();

        // Assert
        verify(healthPort, never()).checkHealth(any(), any());
        verify(repository, never()).save(any(), any(), any(), any(), anyInt(), any(), any());
    }

    @Test
    void schedulePolling_healthAdapterError_returnsDown() {
        // Arrange
        when(observabilityProperties.getEndpoint()).thenReturn("http://localhost:8080");
        when(observabilityProperties.getModel()).thenReturn("test-model");

        LLMHealthPort.LLMStatus healthStatus = new LLMHealthPort.LLMStatus(
            "http://localhost:8080", "test-model", LLMHealthPort.LLMStatus.HealthStatus.DOWN,
            LocalDateTime.now(), 0, List.of(), "Adapter error"
        );
        when(healthPort.checkHealth(any(), any())).thenReturn(Mono.just(healthStatus));

        // Act
        service.schedulePolling();

        // Assert - should persist DOWN status, not throw
        verify(repository, times(1)).save(
            eq("http://localhost:8080"), eq("test-model"), eq("DOWN"),
            any(LocalDateTime.class), eq(0), eq(""), eq("Adapter error")
        );
    }

    // ==================== checkWarnCondition tests ====================

    @Test
    void checkWarnCondition_freshData_unchanged() {
        // Arrange
        LLMHealthPort.LLMStatus status = new LLMHealthPort.LLMStatus(
            "http://localhost:8080", "test-model", LLMHealthPort.LLMStatus.HealthStatus.UP,
            LocalDateTime.now(), 3, List.of("model1"), null
        );
        when(repository.findByEndpoint("http://localhost:8080")).thenReturn(Optional.empty());

        // Act
        LLMHealthPort.LLMStatus result = checkWarnConditionPrivate(status);

        // Assert
        assertEquals(LLMHealthPort.LLMStatus.HealthStatus.UP, result.status());
    }

    @Test
    void checkWarnCondition_staleData_returnsWarn() {
        // Arrange
        LocalDateTime oldTime = LocalDateTime.now().minusHours(2);
        LLMStatusRepository.LLMStatusRecord previousRecord = new LLMStatusRepository.LLMStatusRecord(
            "http://localhost:8080", "test-model", "UP", oldTime, 3, "model1", null
        );
        when(repository.findByEndpoint("http://localhost:8080")).thenReturn(Optional.of(previousRecord));

        LLMHealthPort.LLMStatus status = new LLMHealthPort.LLMStatus(
            "http://localhost:8080", "test-model", LLMHealthPort.LLMStatus.HealthStatus.UP,
            LocalDateTime.now(), 3, List.of("model1"), null
        );

        // Act
        LLMHealthPort.LLMStatus result = checkWarnConditionPrivate(status);

        // Assert
        assertEquals(LLMHealthPort.LLMStatus.HealthStatus.WARN, result.status());
        assertNotNull(result.errorMessage());
        assertTrue(result.errorMessage().contains("hours"));
    }

    @Test
    void checkWarnCondition_recentData_noWarn() {
        // Arrange
        setServiceField("warnAfterHours", 24L);

        LocalDateTime recentTime = LocalDateTime.now().minusMinutes(30);
        LLMStatusRepository.LLMStatusRecord previousRecord = new LLMStatusRepository.LLMStatusRecord(
            "http://localhost:8080", "test-model", "UP", recentTime, 3, "model1", null
        );
        when(repository.findByEndpoint("http://localhost:8080")).thenReturn(Optional.of(previousRecord));

        LLMHealthPort.LLMStatus status = new LLMHealthPort.LLMStatus(
            "http://localhost:8080", "test-model", LLMHealthPort.LLMStatus.HealthStatus.UP,
            LocalDateTime.now(), 3, List.of("model1"), null
        );

        // Act
        LLMHealthPort.LLMStatus result = checkWarnConditionPrivate(status);

        // Assert
        assertEquals(LLMHealthPort.LLMStatus.HealthStatus.UP, result.status());
    }

    @Test
    void checkWarnCondition_alreadyDown_unchanged() {
        // Arrange
        LLMHealthPort.LLMStatus status = new LLMHealthPort.LLMStatus(
            "http://localhost:8080", "test-model", LLMHealthPort.LLMStatus.HealthStatus.DOWN,
            LocalDateTime.now(), 0, List.of(), "Connection refused"
        );

        // Act
        LLMHealthPort.LLMStatus result = checkWarnConditionPrivate(status);

        // Assert
        assertEquals(LLMHealthPort.LLMStatus.HealthStatus.DOWN, result.status());
    }

    @Test
    void checkWarnCondition_noPreviousData_unchanged() {
        // Arrange
        when(repository.findByEndpoint("http://localhost:8080")).thenReturn(Optional.empty());

        LLMHealthPort.LLMStatus status = new LLMHealthPort.LLMStatus(
            "http://localhost:8080", "test-model", LLMHealthPort.LLMStatus.HealthStatus.UP,
            LocalDateTime.now(), 3, List.of("model1"), null
        );

        // Act
        LLMHealthPort.LLMStatus result = checkWarnConditionPrivate(status);

        // Assert
        assertEquals(LLMHealthPort.LLMStatus.HealthStatus.UP, result.status());
    }

    // ==================== triggerPoll() tests ====================

    @Test
    void triggerPoll_success_returnsStatusList() {
        // Arrange
        when(observabilityProperties.getEndpoint()).thenReturn("http://localhost:8080");
        when(observabilityProperties.getModel()).thenReturn("test-model");

        LLMHealthPort.LLMStatus healthStatus = new LLMHealthPort.LLMStatus(
            "http://localhost:8080", "test-model", LLMHealthPort.LLMStatus.HealthStatus.UP,
            LocalDateTime.now(), 3, List.of("model1"), null
        );
        when(healthPort.checkHealth(any(), any())).thenReturn(Mono.just(healthStatus));

        // Act
        List<LLMHealthPort.LLMStatus> result = service.triggerPoll();

        // Assert
        assertEquals(1, result.size());
        assertEquals(LLMHealthPort.LLMStatus.HealthStatus.UP, result.get(0).status());
        verify(repository, times(1)).save(any(), any(), any(), any(), anyInt(), any(), any());
    }

    @Test
    void triggerPoll_nullEndpoint_returnsEmptyList() {
        // Arrange
        when(observabilityProperties.getEndpoint()).thenReturn(null);

        // Act
        List<LLMHealthPort.LLMStatus> result = service.triggerPoll();

        // Assert
        assertTrue(result.isEmpty());
        verify(healthPort, never()).checkHealth(any(), any());
    }

    // ==================== getCurrentStatus() tests ====================

    @Test
    void getCurrentStatus_empty_returnsEmptyList() {
        // Arrange
        when(observabilityProperties.getEndpoint()).thenReturn("http://localhost:8080");
        when(repository.findAll()).thenReturn(List.of());

        // Act
        List<LLMStatusRepository.LLMStatusRecord> result = service.getCurrentStatus();

        // Assert
        assertTrue(result.isEmpty());
    }

    @Test
    void getCurrentStatus_nullEndpoint_returnsEmptyList() {
        // Arrange
        when(observabilityProperties.getEndpoint()).thenReturn(null);

        // Act
        List<LLMStatusRepository.LLMStatusRecord> result = service.getCurrentStatus();

        // Assert
        assertTrue(result.isEmpty());
    }

    @Test
    void getCurrentStatus_withData_returnsRecordList() {
        // Arrange
        when(observabilityProperties.getEndpoint()).thenReturn("http://localhost:8080");
        LocalDateTime now = LocalDateTime.now();
        LLMStatusRepository.LLMStatusRecord record = new LLMStatusRepository.LLMStatusRecord(
            "http://localhost:8080", "test-model", "UP", now, 3, "model1,model2", null
        );
        when(repository.findByEndpoint("http://localhost:8080")).thenReturn(Optional.of(record));

        // Act
        List<LLMStatusRepository.LLMStatusRecord> result = service.getCurrentStatus();

        // Assert
        assertEquals(1, result.size());
        LLMStatusRepository.LLMStatusRecord returned = result.get(0);
        assertEquals("http://localhost:8080", returned.endpoint());
        assertEquals("test-model", returned.configuredModel());
        assertEquals("UP", returned.status());
        assertEquals(3, returned.modelCount());
    }

    @Test
    void getCurrentStatus_entityWithEmptyModelNames_returnsRecord() {
        // Arrange
        when(observabilityProperties.getEndpoint()).thenReturn("http://localhost:8080");
        LocalDateTime now = LocalDateTime.now();
        LLMStatusRepository.LLMStatusRecord record = new LLMStatusRepository.LLMStatusRecord(
            "http://localhost:8080", "test-model", "DOWN", now, 0, "", "Error"
        );
        when(repository.findByEndpoint("http://localhost:8080")).thenReturn(Optional.of(record));

        // Act
        List<LLMStatusRepository.LLMStatusRecord> result = service.getCurrentStatus();

        // Assert
        assertEquals(1, result.size());
    }

    @Test
    void getCurrentStatus_removesStaleEndpoints() {
        // Arrange
        when(observabilityProperties.getEndpoint()).thenReturn("http://configured:8080");
        LocalDateTime now = LocalDateTime.now();

        LLMStatusRepository.LLMStatusRecord configuredRecord = new LLMStatusRepository.LLMStatusRecord(
            "http://configured:8080", "test-model", "UP", now, 1, "model1", null
        );
        LLMStatusRepository.LLMStatusRecord staleRecord = new LLMStatusRepository.LLMStatusRecord(
            "http://old:8080", "old-model", "DOWN", now.minusHours(2), 0, "", "Error"
        );
        LLMStatusRepository.LLMStatusRecord anotherStaleRecord = new LLMStatusRepository.LLMStatusRecord(
            "http://deprecated:8080", "dep-model", "WARN", now.minusDays(1), 0, "", "Timeout"
        );
        when(repository.findAll()).thenReturn(List.of(configuredRecord, staleRecord, anotherStaleRecord));
        when(repository.findByEndpoint("http://configured:8080")).thenReturn(Optional.of(configuredRecord));

        // Act
        List<LLMStatusRepository.LLMStatusRecord> result = service.getCurrentStatus();

        // Assert
        assertEquals(1, result.size());
        assertEquals("http://configured:8080", result.get(0).endpoint());
        verify(repository, times(2)).deleteByEndpoint(any()); // stale endpoints removed
    }

    // ==================== Helper: invoke private checkWarnCondition ====================

    /**
     * Helper to invoke the private checkWarnCondition method for testing.
     * Uses reflection to access the private method.
     */
    private LLMHealthPort.LLMStatus checkWarnConditionPrivate(LLMHealthPort.LLMStatus status) {
        try {
            java.lang.reflect.Method method = AgentStatusService.class
                .getDeclaredMethod("checkWarnCondition", LLMHealthPort.LLMStatus.class);
            method.setAccessible(true);
            return (LLMHealthPort.LLMStatus) method.invoke(service, status);
        } catch (Exception e) {
            throw new RuntimeException("Failed to invoke checkWarnCondition", e);
        }
    }
}

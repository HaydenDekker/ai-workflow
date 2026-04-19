package com.hdekker.ai_workflow.service;

import com.hdekker.ai_workflow.database.llmstatus.LLMStatusEntity;
import com.hdekker.ai_workflow.database.llmstatus.LLMStatusRepository;
import com.hdekker.ai_workflow.llm.OpenAiHealthAdapter;
import com.hdekker.ai_workflow.observability.ObservabilityProperties;
import com.hdekker.ai_workflow.rest.dto.AdapterStatus;
import com.hdekker.ai_workflow.rest.dto.LLMStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for LLMStatusService.
 * 
 * Tests cover:
 * - Scheduled polling (success, failure, null endpoint)
 * - WARN condition detection (stale data, fresh data, already DOWN)
 * - Manual trigger
 * - Entity to DTO conversion
 * - Status retrieval
 */
@ExtendWith(MockitoExtension.class)
class LLMStatusServiceTest {

    @Mock
    private LLMStatusRepository repository;

    @Mock
    private OpenAiHealthAdapter healthAdapter;

    @Mock
    private ObservabilityProperties observabilityProperties;

    @Captor
    private ArgumentCaptor<LLMStatusEntity> entityCaptor;

    private LLMStatusService service;

    @BeforeEach
    void setUp() {
        service = new LLMStatusService(repository, healthAdapter, observabilityProperties);
    }
    
    /**
     * Helper to set private fields via reflection (e.g. @Value fields).
     */
    private void setServiceField(String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = LLMStatusService.class.getDeclaredField(fieldName);
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
        
        LLMStatus healthStatus = new LLMStatus(
            "http://localhost:8080", "test-model", AdapterStatus.UP,
            LocalDateTime.now(), 3, List.of("model1", "model2", "model3"), null
        );
        when(healthAdapter.checkHealth(any(), any())).thenReturn(Mono.just(healthStatus));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Act
        service.schedulePolling();

        // Assert
        verify(repository, times(1)).save(entityCaptor.capture());
        LLMStatusEntity saved = entityCaptor.getValue();
        assertEquals("http://localhost:8080", saved.getEndpoint());
        assertEquals("test-model", saved.getConfiguredModel());
        assertEquals("UP", saved.getStatus());
        assertEquals(3, saved.getModelCount());
        assertEquals("model1,model2,model3", saved.getModelNames());
        assertNull(saved.getErrorMessage());
    }

    @Test
    void schedulePolling_failure_persistsDownStatus() {
        // Arrange
        when(observabilityProperties.getEndpoint()).thenReturn("http://localhost:8080");
        when(observabilityProperties.getModel()).thenReturn("test-model");
        
        LLMStatus healthStatus = new LLMStatus(
            "http://localhost:8080", "test-model", AdapterStatus.DOWN,
            LocalDateTime.now(), 0, List.of(), "Connection refused"
        );
        when(healthAdapter.checkHealth(any(), any())).thenReturn(Mono.just(healthStatus));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Act
        service.schedulePolling();

        // Assert
        verify(repository, times(1)).save(entityCaptor.capture());
        LLMStatusEntity saved = entityCaptor.getValue();
        assertEquals("DOWN", saved.getStatus());
        assertEquals("Connection refused", saved.getErrorMessage());
    }

    @Test
    void schedulePolling_nullEndpoint_skipsHealthCheck() {
        // Arrange
        when(observabilityProperties.getEndpoint()).thenReturn(null);

        // Act
        service.schedulePolling();

        // Assert
        verify(healthAdapter, never()).checkHealth(any(), any());
        verify(repository, never()).save(any());
    }

    @Test
    void schedulePolling_emptyEndpoint_skipsHealthCheck() {
        // Arrange
        when(observabilityProperties.getEndpoint()).thenReturn("");

        // Act
        service.schedulePolling();

        // Assert
        verify(healthAdapter, never()).checkHealth(any(), any());
        verify(repository, never()).save(any());
    }

    @Test
    void schedulePolling_healthAdapterError_returnsDown() {
        // Arrange
        when(observabilityProperties.getEndpoint()).thenReturn("http://localhost:8080");
        when(observabilityProperties.getModel()).thenReturn("test-model");
        
        LLMStatus healthStatus = new LLMStatus(
            "http://localhost:8080", "test-model", AdapterStatus.DOWN,
            LocalDateTime.now(), 0, List.of(), "Adapter error"
        );
        when(healthAdapter.checkHealth(any(), any())).thenReturn(Mono.just(healthStatus));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Act
        service.schedulePolling();

        // Assert - should persist DOWN status, not throw
        verify(repository, times(1)).save(entityCaptor.capture());
        assertEquals("DOWN", entityCaptor.getValue().getStatus());
    }

    // ==================== checkWarnCondition tests ====================

    @Test
    void checkWarnCondition_freshData_unchanged() {
        // Arrange
        LLMStatus status = new LLMStatus(
            "http://localhost:8080", "test-model", AdapterStatus.UP,
            LocalDateTime.now(), 3, List.of("model1"), null
        );
        when(repository.findByEndpoint("http://localhost:8080")).thenReturn(Optional.empty());

        // Act
        LLMStatus result = checkWarnConditionPrivate(status);

        // Assert
        assertEquals(AdapterStatus.UP, result.status());
    }

    @Test
    void checkWarnCondition_staleData_returnsWarn() {
        // Arrange
        LocalDateTime oldTime = LocalDateTime.now().minusHours(2);
        LLMStatusEntity previousEntity = new LLMStatusEntity(
            "http://localhost:8080", "test-model", "UP", oldTime, 3, "model1", null
        );
        when(repository.findByEndpoint("http://localhost:8080")).thenReturn(Optional.of(previousEntity));
        
        LLMStatus status = new LLMStatus(
            "http://localhost:8080", "test-model", AdapterStatus.UP,
            LocalDateTime.now(), 3, List.of("model1"), null
        );

        // Act
        LLMStatus result = checkWarnConditionPrivate(status);

        // Assert
        assertEquals(AdapterStatus.WARN, result.status());
        assertNotNull(result.errorMessage());
        assertTrue(result.errorMessage().contains("hours"));
    }

    @Test
    void checkWarnCondition_recentData_noWarn() {
        // Arrange
        setServiceField("warnAfterHours", 24L);
        
        LocalDateTime recentTime = LocalDateTime.now().minusMinutes(30);
        LLMStatusEntity previousEntity = new LLMStatusEntity(
            "http://localhost:8080", "test-model", "UP", recentTime, 3, "model1", null
        );
        when(repository.findByEndpoint("http://localhost:8080")).thenReturn(Optional.of(previousEntity));
        
        LLMStatus status = new LLMStatus(
            "http://localhost:8080", "test-model", AdapterStatus.UP,
            LocalDateTime.now(), 3, List.of("model1"), null
        );

        // Act
        LLMStatus result = checkWarnConditionPrivate(status);

        // Assert
        assertEquals(AdapterStatus.UP, result.status());
    }

    @Test
    void checkWarnCondition_alreadyDown_unchanged() {
        // Arrange
        LLMStatus status = new LLMStatus(
            "http://localhost:8080", "test-model", AdapterStatus.DOWN,
            LocalDateTime.now(), 0, List.of(), "Connection refused"
        );

        // Act
        LLMStatus result = checkWarnConditionPrivate(status);

        // Assert
        assertEquals(AdapterStatus.DOWN, result.status());
    }

    @Test
    void checkWarnCondition_noPreviousData_unchanged() {
        // Arrange
        when(repository.findByEndpoint("http://localhost:8080")).thenReturn(Optional.empty());
        
        LLMStatus status = new LLMStatus(
            "http://localhost:8080", "test-model", AdapterStatus.UP,
            LocalDateTime.now(), 3, List.of("model1"), null
        );

        // Act
        LLMStatus result = checkWarnConditionPrivate(status);

        // Assert
        assertEquals(AdapterStatus.UP, result.status());
    }

    // ==================== triggerPoll() tests ====================

    @Test
    void triggerPoll_success_returnsStatusList() {
        // Arrange
        when(observabilityProperties.getEndpoint()).thenReturn("http://localhost:8080");
        when(observabilityProperties.getModel()).thenReturn("test-model");
        
        LLMStatus healthStatus = new LLMStatus(
            "http://localhost:8080", "test-model", AdapterStatus.UP,
            LocalDateTime.now(), 3, List.of("model1"), null
        );
        when(healthAdapter.checkHealth(any(), any())).thenReturn(Mono.just(healthStatus));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Act
        List<LLMStatus> result = service.triggerPoll();

        // Assert
        assertEquals(1, result.size());
        assertEquals(AdapterStatus.UP, result.get(0).status());
        verify(repository, times(1)).save(any());
    }

    @Test
    void triggerPoll_nullEndpoint_returnsEmptyList() {
        // Arrange
        when(observabilityProperties.getEndpoint()).thenReturn(null);

        // Act
        List<LLMStatus> result = service.triggerPoll();

        // Assert
        assertTrue(result.isEmpty());
        verify(healthAdapter, never()).checkHealth(any(), any());
    }

    // ==================== getCurrentStatus() tests ====================

    @Test
    void getCurrentStatus_empty_returnsEmptyList() {
        // Arrange
        when(observabilityProperties.getEndpoint()).thenReturn("http://localhost:8080");
        when(repository.findAll()).thenReturn(List.of());

        // Act
        List<LLMStatus> result = service.getCurrentStatus();

        // Assert
        assertTrue(result.isEmpty());
    }

    @Test
    void getCurrentStatus_nullEndpoint_returnsEmptyList() {
        // Arrange
        when(observabilityProperties.getEndpoint()).thenReturn(null);

        // Act
        List<LLMStatus> result = service.getCurrentStatus();

        // Assert
        assertTrue(result.isEmpty());
    }

    @Test
    void getCurrentStatus_withData_returnsDtoList() {
        // Arrange
        when(observabilityProperties.getEndpoint()).thenReturn("http://localhost:8080");
        LocalDateTime now = LocalDateTime.now();
        LLMStatusEntity entity = new LLMStatusEntity(
            "http://localhost:8080", "test-model", "UP", now, 3, "model1,model2", null
        );
        when(repository.findByEndpoint("http://localhost:8080")).thenReturn(Optional.of(entity));

        // Act
        List<LLMStatus> result = service.getCurrentStatus();

        // Assert
        assertEquals(1, result.size());
        LLMStatus dto = result.get(0);
        assertEquals("http://localhost:8080", dto.endpoint());
        assertEquals("test-model", dto.configuredModel());
        assertEquals(AdapterStatus.UP, dto.status());
        assertEquals(3, dto.modelCount());
        assertEquals(2, dto.modelNames().size());
        assertEquals("model1", dto.modelNames().get(0));
    }

    @Test
    void getCurrentStatus_entityWithEmptyModelNames_returnsEmptyList() {
        // Arrange
        when(observabilityProperties.getEndpoint()).thenReturn("http://localhost:8080");
        LocalDateTime now = LocalDateTime.now();
        LLMStatusEntity entity = new LLMStatusEntity(
            "http://localhost:8080", "test-model", "DOWN", now, 0, "", "Error"
        );
        when(repository.findByEndpoint("http://localhost:8080")).thenReturn(Optional.of(entity));

        // Act
        List<LLMStatus> result = service.getCurrentStatus();

        // Assert
        assertEquals(1, result.size());
        assertTrue(result.get(0).modelNames().isEmpty());
    }

    @Test
    void getCurrentStatus_removesStaleEndpoints() {
        // Arrange
        when(observabilityProperties.getEndpoint()).thenReturn("http://configured:8080");
        LocalDateTime now = LocalDateTime.now();

        LLMStatusEntity configuredEntity = new LLMStatusEntity(
            "http://configured:8080", "test-model", "UP", now, 1, "model1", null
        );
        LLMStatusEntity staleEntity = new LLMStatusEntity(
            "http://old:8080", "old-model", "DOWN", now.minusHours(2), 0, "", "Error"
        );
        LLMStatusEntity anotherStaleEntity = new LLMStatusEntity(
            "http://deprecated:8080", "dep-model", "WARN", now.minusDays(1), 0, "", "Timeout"
        );
        when(repository.findAll()).thenReturn(List.of(configuredEntity, staleEntity, anotherStaleEntity));
        when(repository.findByEndpoint("http://configured:8080")).thenReturn(Optional.of(configuredEntity));

        // Act
        List<LLMStatus> result = service.getCurrentStatus();

        // Assert
        assertEquals(1, result.size());
        assertEquals("http://configured:8080", result.get(0).endpoint());
        verify(repository, times(2)).delete(any()); // stale entities removed
    }

    // ==================== Helper: invoke private checkWarnCondition ====================

    /**
     * Helper to invoke the private checkWarnCondition method for testing.
     * Uses reflection to access the private method.
     */
    private LLMStatus checkWarnConditionPrivate(LLMStatus status) {
        try {
            java.lang.reflect.Method method = LLMStatusService.class
                .getDeclaredMethod("checkWarnCondition", LLMStatus.class);
            method.setAccessible(true);
            return (LLMStatus) method.invoke(service, status);
        } catch (Exception e) {
            throw new RuntimeException("Failed to invoke checkWarnCondition", e);
        }
    }
}

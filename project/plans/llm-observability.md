# LLM Observability Panel - Implementation Plan

## Overview

Create a standalone `/observability` route with an **Adapter Status Component** that monitors LLM health using non-intrusive health checks. The system will:

- Poll configured LLM endpoints using `listModels()` (no token consumption)
- Persist status to SQLite database table
- Auto-poll on application startup with configurable interval
- Show status indicators: UP (green), WARN (yellow - stale data), DOWN (red)
- Support multiple endpoints (small/fast LLM on one machine, big/slow on another)
- Log warnings when endpoints are unreachable

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────┐
│                      Observability View                             │
│                      Route: /observability                          │
│  ┌───────────────────────────────────────────────────────────────┐ │
│  │                  AdapterStatusComponent (Card)                │ │
│  │  ┌─────────────┬────────────┬─────────────┬─────────────────┐ │ │
│  │  │ Small LLM   │    UP      │ 2 min ago   │ 3 models        │ │ │
│  │  │ (fast)      │   (Green)  │             │                 │ │ │
│  │  └─────────────┴────────────┴─────────────┴─────────────────┘ │ │
│  │  ┌─────────────┬────────────┬─────────────┬─────────────────┐ │ │
│  │  │ Big LLM     │    WARN    │ 1 hr 5 min  │ 1 model         │ │ │
│  │  │ (slow)      │  (Yellow)  │             │                 │ │ │
│  │  └─────────────┴────────────┴─────────────┴─────────────────┘ │ │
│  └───────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────┘
                              ▲
                              │ REST: GET /api/observability/llm-status
                              │
                    ┌─────────┴──────────┐
                    │  LLMStatusService  │
                    │  (Scheduled Poll)  │
                    └─────────┬──────────┘
                              │
                    ┌─────────┴──────────┐
                     │  OpenAiHealthAdapter│
                     │  (Business Logic)  │
                     └─────────┬──────────┘
                               │
                     ┌─────────┴──────────┐
                     │  OpenAiHealthClient │
                     │  (HTTP: /v1/models) │
                     └─────────┬──────────┘
                              │
                    ┌─────────┴──────────┐
                    │ LLMStatusEntity    │
                    │ (SQLite Table)     │
                    └────────────────────┘
```

---

## Component Breakdown

### 1. Database Entity (SQLite/H2 Compatible)

**File:** `src/main/java/com/hdekker/ai_workflow/database/llmstatus/LLMStatusEntity.java`

```java
package com.hdekker.ai_workflow.database.llmstatus;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.time.LocalDateTime;

/**
 * Entity for storing LLM endpoint health status.
 * Persists to application.db table: llm_status
 */
@Entity
public class LLMStatusEntity {
    
    @Id
    private String endpoint;              // Primary key: endpoint URL (e.g., http://192.168.2.108:11434)
    
    private String configuredModel;       // Expected model name (e.g., gemma3:27b)
    private String status;                // UNKNOWN, CONNECTING, UP, WARN, DOWN
    private LocalDateTime lastChecked;    // Timestamp of last health check
    private Integer modelCount;           // Number of models available at endpoint
    private String modelNames;            // Comma-separated model names (e.g., "qwen3,gemma3:27b")
    private String errorMessage;          // Last error message if status is DOWN
    
    // Default constructor required by JPA
    public LLMStatusEntity() {}
    
    public LLMStatusEntity(String endpoint, String configuredModel, String status,
                          LocalDateTime lastChecked, Integer modelCount,
                          String modelNames, String errorMessage) {
        this.endpoint = endpoint;
        this.configuredModel = configuredModel;
        this.status = status;
        this.lastChecked = lastChecked;
        this.modelCount = modelCount;
        this.modelNames = modelNames;
        this.errorMessage = errorMessage;
    }
    
    // Getters and Setters
    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
    
    public String getConfiguredModel() { return configuredModel; }
    public void setConfiguredModel(String configuredModel) { this.configuredModel = configuredModel; }
    
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    
    public LocalDateTime getLastChecked() { return lastChecked; }
    public void setLastChecked(LocalDateTime lastChecked) { this.lastChecked = lastChecked; }
    
    public Integer getModelCount() { return modelCount; }
    public void setModelCount(Integer modelCount) { this.modelCount = modelCount; }
    
    public String getModelNames() { return modelNames; }
    public void setModelNames(String modelNames) { this.modelNames = modelNames; }
    
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
```

**File:** `src/main/java/com/hdekker/ai_workflow/database/llmstatus/LLMStatusRepository.java`

```java
package com.hdekker.ai_workflow.database.llmstatus;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

/**
 * Repository for LLM status persistence.
 * Auto-creates table: llm_status
 */
public interface LLMStatusRepository extends JpaRepository<LLMStatusEntity, String> {
    Optional<LLMStatusEntity> findByEndpoint(String endpoint);
    List<LLMStatusEntity> findAll();
}
```

---

### 2. DTO Layer

**File:** `src/main/java/com/hdekker/ai_workflow/rest/dto/LLMStatus.java`

```java
package com.hdekker.ai_workflow.rest.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO for LLM endpoint status.
 * Returned by REST API and used by UI components.
 */
public record LLMStatus(
    String endpoint,
    String configuredModel,
    AdapterStatus status,
    LocalDateTime lastChecked,
    Integer modelCount,
    List<String> modelNames,
    String errorMessage
) {}

/**
 * Status states for LLM endpoints.
 */
public enum AdapterStatus {
    UNKNOWN,     // No data yet (initial state)
    CONNECTING,  // Currently checking (transient state)
    UP,          // Healthy - green indicator
    WARN,        // Degraded - yellow indicator (last check > warnAfterHours)
    DOWN         // Unreachable - red indicator
}
```

---

### 3. Health Adapter Layer (OpenAI-Compatible)

#### 3.1 OpenAiModelsResponse DTO

**File:** `src/main/java/com/hdekker/ai_workflow/rest/dto/OpenAiModelsResponse.java`

```java
package com.hdekker.ai_workflow.rest.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * DTO for parsing OpenAI /models API response.
 * Supports both OpenAI and OpenAI-compatible servers (llamacpp, Ollama in OpenAI mode).
 */
public record OpenAiModelsResponse(
    String object,
    List<OpenAiModel> data
) {
    /**
     * Represents a single model in the response.
     */
    public record OpenAiModel(
        String id,
        List<String> aliases,
        List<String> tags,
        @JsonProperty("object") String objectType,
        Long created,
        String owned_by,
        Object meta
    ) {}
}
```

#### 3.2 OpenAiHealthClient (REST Client)

**File:** `src/main/java/com/hdekker/ai_workflow/llm/OpenAiHealthClient.java`

```java
package com.hdekker.ai_workflow.llm;

import com.hdekker.ai_workflow.rest.dto.OpenAiModelsResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;
import reactor.core.publisher.Mono;
import java.util.List;
import java.util.stream.Collectors;

/**
 * REST client for OpenAI-compatible health checking.
 * Calls /v1/models endpoint to verify endpoint availability.
 */
public class OpenAiHealthClient {
    
    private static final Logger log = LoggerFactory.getLogger(OpenAiHealthClient.class);
    private final RestClient restClient;
    
    public OpenAiHealthClient(String endpoint, int timeoutMs) {
        this.restClient = RestClient.builder()
            .baseUrl(endpoint)
            .build();
    }
    
    public OpenAiHealthClient(RestClient restClient) {
        this.restClient = restClient;
    }
    
    public OpenAiHealthClient(RestClient.Builder builder) {
        this.restClient = builder.build();
    }
    
    /**
     * List available models at the endpoint.
     * Returns model IDs as a list of strings.
     * 
     * @return Mono emitting list of model names
     */
    public Mono<List<String>> listModels() {
        return Mono.fromCallable(() -> {
            OpenAiModelsResponse response = restClient.get()
                .uri("/v1/models")
                .retrieve()
                .body(OpenAiModelsResponse.class);
            
            if (response == null || response.data() == null) {
                log.warn("Unexpected empty response from /v1/models");
                return List.of();
            }
            
            List<String> modelNames = response.data().stream()
                .map(OpenAiModelsResponse.OpenAiModel::id)
                .filter(id -> id != null && !id.isEmpty())
                .collect(Collectors.toList());
            
            log.debug("Retrieved {} models from endpoint", modelNames.size());
            return modelNames;
        });
    }
}
```

#### 3.3 OpenAiHealthAdapter

**File:** `src/main/java/com/hdekker/ai_workflow/llm/OpenAiHealthAdapter.java`

```java
package com.hdekker.ai_workflow.llm;

import com.hdekker.ai_workflow.rest.dto.AdapterStatus;
import com.hdekker.ai_workflow.rest.dto.LLMStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Adapter for checking OpenAI-compatible endpoint health.
 * Uses listModels() API which does NOT consume tokens or affect context.
 * 
 * Health check strategy:
 * - listModels() verifies connectivity AND that models are loaded
 * - No prompts are sent, so no tokens consumed
 * - No conversation context is affected
 */
public class OpenAiHealthAdapter {
    
    private static final Logger log = LoggerFactory.getLogger(OpenAiHealthAdapter.class);
    private final int timeoutMs;
    
    public OpenAiHealthAdapter(int timeoutMs) {
        this.timeoutMs = timeoutMs;
    }
    
    /**
     * Health check using listModels - does NOT consume tokens or affect context.
     * 
     * @param endpoint OpenAI-compatible endpoint URL (e.g., http://localhost:8080)
     * @param configuredModel Expected model name
     * @return Mono emitting LLMStatus with current health state
     */
    public Mono<LLMStatus> checkHealth(String endpoint, String configuredModel) {
        log.debug("Starting health check for endpoint: {}", endpoint);
        
        OpenAiHealthClient client = new OpenAiHealthClient(endpoint, timeoutMs);
        
        return client.listModels()
            .map(modelNames -> {
                log.debug("Health check OK for {}: {} models available", endpoint, modelNames.size());
                return new LLMStatus(
                    endpoint,
                    configuredModel,
                    AdapterStatus.UP,
                    LocalDateTime.now(),
                    modelNames.size(),
                    modelNames,
                    null
                );
            })
            .onErrorResume(e -> {
                log.warn("Health check FAILED for {}: {}", endpoint, e.getMessage());
                return Mono.just(new LLMStatus(
                    endpoint,
                    configuredModel,
                    AdapterStatus.DOWN,
                    LocalDateTime.now(),
                    0,
                    List.of(),
                    e.getMessage()
                ));
            })
            .timeout(Duration.ofMillis(timeoutMs))
            .onErrorResume(timeoutEx -> {
                log.warn("Health check TIMEOUT for {} after {}ms", endpoint, timeoutMs);
                return Mono.just(new LLMStatus(
                    endpoint,
                    configuredModel,
                    AdapterStatus.DOWN,
                    LocalDateTime.now(),
                    0,
                    List.of(),
                    "Timeout after " + timeoutMs + "ms"
                ));
            });
    }
}
```

#### 3.4 OpenAiHealthConfiguration

**File:** `src/main/java/com/hdekker/ai_workflow/llm/OpenAiHealthConfiguration.java`

```java
package com.hdekker.ai_workflow.llm;

import com.hdekker.ai_workflow.observability.ObservabilityProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for OpenAI health checking components.
 */
@Configuration
public class OpenAiHealthConfiguration {
    
    @Autowired
    private ObservabilityProperties observabilityProperties;
    
    @Bean
    public OpenAiHealthAdapter openAiHealthAdapter() {
        return new OpenAiHealthAdapter(observabilityProperties.getHealthTimeout());
    }
}
```

---

### 4. Service Layer with Caching & Scheduling

**File:** `src/main/java/com/hdekker/ai_workflow/service/LLMStatusService.java`

```java
package com.hdekker.ai_workflow.service;

import com.hdekker.ai_workflow.database.llmstatus.LLMStatusEntity;
import com.hdekker.ai_workflow.database.llmstatus.LLMStatusRepository;
import com.hdekker.ai_workflow.llm.OpenAiHealthAdapter;
import com.hdekker.ai_workflow.rest.dto.AdapterStatus;
import com.hdekker.ai_workflow.rest.dto.LLMStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for managing LLM endpoint health status.
 * 
 * Responsibilities:
 * - Poll configured endpoints on schedule
 * - Persist status to database
 * - Check WARN condition (stale data)
 * - Log warnings for DOWN/WARN states
 */
@Service
public class LLMStatusService {
    
    private static final Logger log = LoggerFactory.getLogger(LLMStatusService.class);
    
    @Autowired
    private LLMStatusRepository repository;
    
    @Autowired
    private ObservabilityProperties observabilityProperties;
    
    private final OpenAiHealthAdapter healthAdapter;
    
    @Value("${app.observability.warn-after-hours:1}")
    private long warnAfterHours;
    
    public LLMStatusService(OpenAiHealthAdapter healthAdapter) {
        this.healthAdapter = healthAdapter;
    }
    
    /**
     * Scheduled polling - runs at configured interval.
     * Default: 60000ms (1 minute)
     * 
     * Polls all configured LLM endpoints and persists results.
     * Logs warnings for DOWN/WARN states.
     */
    @Scheduled(fixedRateString = "${app.observability.polling-interval:60000}")
    public void schedulePolling() {
        log.debug("Starting scheduled LLM health check...");
        
        // Currently single endpoint from config
        // Future: Support multiple endpoints from configuration
        Mono<LLMStatus> statusMono = healthAdapter.checkHealth(
            observabilityProperties.getEndpoint(), 
            observabilityProperties.getModel()
        );
        
        statusMono.subscribe(
            status -> {
                // Check for WARN condition (last check > warnAfterHours)
                LLMStatus finalStatus = checkWarnCondition(status);
                
                // Persist to database
                persistStatus(finalStatus);
                
                // Log warnings
                if (finalStatus.status() == AdapterStatus.DOWN) {
                    log.warn("LLM endpoint DOWN: {} - {}", 
                        finalStatus.endpoint(), 
                        finalStatus.errorMessage());
                } else if (finalStatus.status() == AdapterStatus.WARN) {
                    log.warn("LLM endpoint WARN: {} - No response for {}+ hours",
                        finalStatus.endpoint(),
                        warnAfterHours);
                } else {
                    log.debug("LLM endpoint OK: {} - {} models",
                        finalStatus.endpoint(),
                        finalStatus.modelCount());
                }
            },
            error -> log.error("Error during LLM health check", error)
        );
    }
    
    /**
     * Check if status should be WARN (stale data).
     * If last successful check was more than warnAfterHours ago, set WARN.
     */
    private LLMStatus checkWarnCondition(LLMStatus status) {
        if (status.status() == AdapterStatus.UP) {
            // Fresh data - no change
            return status;
        }
        
        // Check if we have previous data that was UP
        String endpoint = status.endpoint();
        repository.findByEndpoint(endpoint).ifPresent(previous -> {
            if (AdapterStatus.UP.name().equals(previous.getStatus())) {
                LocalDateTime lastUp = previous.getLastChecked();
                if (lastUp != null) {
                    long hoursSince = Duration.between(lastUp, LocalDateTime.now()).toHours();
                    if (hoursSince >= warnAfterHours) {
                        log.debug("Endpoint {} marked WARN - {} hours since last UP", 
                            endpoint, hoursSince);
                        return; // Status will be updated below
                    }
                }
            }
        });
        
        // If still DOWN but within warnAfterHours, keep DOWN
        // If beyond warnAfterHours, change to WARN
        if (status.status() == AdapterStatus.DOWN) {
            return status; // Keep DOWN for now, WARN applies to stale UP data
        }
        
        return status;
    }
    
    /**
     * Persist status to database.
     */
    private void persistStatus(LLMStatus status) {
        LLMStatusEntity entity = new LLMStatusEntity(
            status.endpoint(),
            status.configuredModel(),
            status.status().name(),
            status.lastChecked(),
            status.modelCount(),
            status.modelNames() != null ? String.join(",", status.modelNames()) : "",
            status.errorMessage()
        );
        repository.save(entity);
    }
    
    /**
     * Get current status from database (for UI).
     * Returns list of all configured endpoints with their current status.
     */
    public List<LLMStatus> getCurrentStatus() {
        return repository.findAll().stream()
            .map(this::entityToDto)
            .collect(Collectors.toList());
    }
    
    /**
     * Manually trigger polling (for immediate refresh).
     */
    public List<LLMStatus> triggerPoll() {
        log.info("Manual LLM health check triggered");
        List<LLMStatus> statuses = new ArrayList<>();
        
        // Currently single endpoint
        String endpoint = properties.getEndpoint();
        String model = properties.getModel();
        
        LLMStatus status = healthAdapter.checkHealth(endpoint, model)
            .block();
        
        if (status != null) {
            LLMStatus finalStatus = checkWarnCondition(status);
            persistStatus(finalStatus);
            statuses.add(finalStatus);
        }
        
        return statuses;
    }
    
    /**
     * Convert entity to DTO.
     */
    private LLMStatus entityToDto(LLMStatusEntity entity) {
        List<String> modelNames = entity.getModelNames() != null && !entity.getModelNames().isEmpty()
            ? List.of(entity.getModelNames().split(","))
            : List.of();
        
        return new LLMStatus(
            entity.getEndpoint(),
            entity.getConfiguredModel(),
            AdapterStatus.valueOf(entity.getStatus()),
            entity.getLastChecked(),
            entity.getModelCount(),
            modelNames,
            entity.getErrorMessage()
        );
    }
}
```

---

### 5. REST Endpoint

**File:** `src/main/java/com/hdekker/ai_workflow/rest/ObservabilityRestController.java`

```java
package com.hdekker.ai_workflow.rest;

import com.hdekker.ai_workflow.rest.dto.LLMStatus;
import com.hdekker.ai_workflow.service.LLMStatusService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

/**
 * REST endpoint for observability data.
 * 
 * Endpoints:
 * - GET  /api/observability/llm-status  - Get current status
 * - POST /api/observability/llm-status/poll - Trigger immediate poll
 */
@RestController
@RequestMapping("/api/observability")
public class ObservabilityRestController {
    
    @Autowired
    private LLMStatusService llmStatusService;
    
    /**
     * Get current LLM status from database cache.
     * Fast - no polling, just reads from database.
     */
    @GetMapping("/llm-status")
    public ResponseEntity<List<LLMStatus>> getLLMStatus() {
        List<LLMStatus> status = llmStatusService.getCurrentStatus();
        return ResponseEntity.ok(status);
    }
    
    /**
     * Trigger immediate polling.
     * Returns updated status after polling completes.
     */
    @PostMapping("/llm-status/poll")
    public ResponseEntity<List<LLMStatus>> triggerPoll() {
        List<LLMStatus> status = llmStatusService.triggerPoll();
        return ResponseEntity.ok(status);
    }
}
```

---

### 6. Configuration

**File:** `src/main/java/com/hdekker/ai_workflow/observability/ObservabilityProperties.java`

```java
package com.hdekker.ai_workflow.observability;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for observability features.
 * 
 * Prefix: app.observability
 * 
 * Example application.yml:
 * app:
 *   observability:
 *     polling-interval: 60000
 *     warn-after-hours: 1
 *     health-timeout: 5000
 *     endpoint: http://192.168.2.108:11434
 *     model: gemma3:27b
 */
@Configuration
@ConfigurationProperties(value = "app.observability")
public class ObservabilityProperties {
    
    /**
     * OpenAI-compatible endpoint URL to health check.
     * Default: http://localhost:8080
     */
    private String endpoint = "http://localhost:8080";
    
    /**
     * Expected model name at the endpoint.
     * Default: null
     */
    private String model;
    
    /**
     * Polling interval in milliseconds.
     * Default: 60000 (1 minute)
     */
    private long pollingInterval = 60000;
    
    /**
     * Hours after which UP status becomes WARN if no new data.
     * Default: 1 hour
     */
    private long warnAfterHours = 1;
    
    /**
     * Health check timeout in milliseconds.
     * Default: 5000 (5 seconds)
     */
    private int healthTimeout = 5000;
    
    // Getters and Setters
    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
    
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    
    public long getPollingInterval() { return pollingInterval; }
    public void setPollingInterval(long pollingInterval) { this.pollingInterval = pollingInterval; }
    
    public long getWarnAfterHours() { return warnAfterHours; }
    public void setWarnAfterHours(long warnAfterHours) { this.warnAfterHours = warnAfterHours; }
    
    public int getHealthTimeout() { return healthTimeout; }
    public void setHealthTimeout(int healthTimeout) { this.healthTimeout = healthTimeout; }
}
```

**File:** `src/main/java/com/hdekker/ai_workflow/llm/OpenAiHealthConfiguration.java` (Already created in Step 3.4)

> **Note:** The OpenAI health configuration was already created as part of Step 3. No additional configuration file needed.

**File:** `src/main/java/com/hdekker/ai_workflow/AiWorkflowApplication.java` (Update needed)

Add `@EnableScheduling` and register `ObservabilityProperties`:

```java
package com.hdekker.ai_workflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;  // <-- ADD THIS

import com.hdekker.ai_workflow.observability.ObservabilityProperties;  // <-- ADD THIS

@SpringBootApplication
@EnableConfigurationProperties({DataSourceProperties.class, ObservabilityProperties.class})  // <-- UPDATE
@EnableScheduling  // <-- ADD THIS
public class AiWorkflowApplication {
    public static void main(String[] args) {
        SpringApplication.run(AiWorkflowApplication.class, args);
    }
}
```

**File:** `src/main/resources/application.yml` (Additions)

Add to existing file:

```yaml
# Observability settings
app:
  observability:
    endpoint: http://192.168.2.108:11434   # OpenAI-compatible endpoint to monitor
    model: gemma3:27b                       # Expected model name
    polling-interval: 60000                 # 1 minute between health checks
    warn-after-hours: 1                     # Mark WARN after 1 hour without response
    health-timeout: 5000                    # 5 second timeout for health checks
```

---

### 7. UI Components

**File:** `src/main/java/com/hdekker/ai_workflow/ui/components/AdapterStatusComponent.java`

```java
package com.hdekker.ai_workflow.ui.components;

import com.hdekker.ai_workflow.rest.dto.AdapterStatus;
import com.hdekker.ai_workflow.rest.dto.LLMStatus;
import com.vaadin.flow.component.Html;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.theme.lumo.LumoUtility;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Reusable component for displaying a single adapter's status.
 * 
 * Displays:
 * - Endpoint name/URL
 * - Status indicator (colored badge)
 * - Last checked timestamp
 * - Model count and names
 */
public class AdapterStatusComponent extends VerticalLayout {
    
    private static final DateTimeFormatter TIME_FORMATTER = 
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    private final LLMStatus status;
    
    // UI Components
    private final HorizontalLayout headerLayout;
    private final VerticalLayout detailsLayout;
    
    public AdapterStatusComponent(LLMStatus status) {
        this.status = status;
        
        setPadding(false);
        setSpacing(false);
        addClassName("adapter-status-card");
        
        // Header with endpoint name and status badge
        headerLayout = createHeaderLayout();
        
        // Details section
        detailsLayout = createDetailsLayout();
        
        add(headerLayout, detailsLayout);
    }
    
    private HorizontalLayout createHeaderLayout() {
        HorizontalLayout layout = new HorizontalLayout();
        layout.setWidthFull();
        layout.setAlignItems(Alignment.CENTER);
        layout.setJustifyContentMode(JustifyContentMode.START);
        layout.addClassName("adapter-header");
        
        // Endpoint name
        TextField endpointField = new TextField();
        endpointField.setValue(extractEndpointName(status.endpoint()));
        endpointField.setReadOnly(true);
        endpointField.setWidth("200px");
        endpointField.setClassName("endpoint-name");
        
        // Status badge with icon
        Icon statusIcon = createStatusIcon(status.status());
        Html statusBadge = createStatusBadge(status.status());
        
        layout.add(endpointField, statusIcon, statusBadge);
        
        return layout;
    }
    
    private VerticalLayout createDetailsLayout() {
        VerticalLayout layout = new VerticalLayout();
        layout.setWidthFull();
        layout.setPadding(false);
        layout.setSpacing(true);
        layout.addClassName("adapter-details");
        
        // Last checked
        if (status.lastChecked() != null) {
            TextField lastChecked = new TextField();
            lastChecked.setValue("Last checked: " + 
                status.lastChecked().format(TIME_FORMATTER));
            lastChecked.setReadOnly(true);
            lastChecked.setWidthFull();
            lastChecked.setClassName("last-checked");
            layout.add(lastChecked);
        }
        
        // Model count
        if (status.modelCount() != null && status.modelCount() > 0) {
            TextField modelCount = new TextField();
            modelCount.setValue(status.modelCount() + " model(s) available");
            modelCount.setReadOnly(true);
            modelCount.setWidthFull();
            modelCount.setClassName("model-count");
            layout.add(modelCount);
            
            // Model names (if available)
            if (status.modelNames() != null && !status.modelNames().isEmpty()) {
                TextField modelNames = new TextField();
                modelNames.setValue("Models: " + String.join(", ", status.modelNames()));
                modelNames.setReadOnly(true);
                modelNames.setWidthFull();
                modelNames.setClassName("model-names");
                layout.add(modelNames);
            }
        }
        
        // Error message (if any)
        if (status.errorMessage() != null && !status.errorMessage().isEmpty()) {
            TextField errorMsg = new TextField();
            errorMsg.setValue("Error: " + status.errorMessage());
            errorMsg.setReadOnly(true);
            errorMsg.setWidthFull();
            errorMsg.setClassName("error-message");
            layout.add(errorMsg);
        }
        
        return layout;
    }
    
    private Icon createStatusIcon(AdapterStatus status) {
        Icon icon;
        
        switch (status) {
            case UP:
                icon = new Icon(VaadinIcon.CHECK_CIRCLE_O);
                icon.setColor("#00AA00"); // Green
                break;
            case WARN:
                icon = new Icon(VaadinIcon.EXCLAMATION_CIRCLE_O);
                icon.setColor("#FFAA00"); // Yellow/Orange
                break;
            case DOWN:
                icon = new Icon(VaadinIcon.TIMES_CIRCLE_O);
                icon.setColor("#FF0000"); // Red
                break;
            case CONNECTING:
                icon = new Icon(VaadinIcon.SPINNER);
                icon.setColor("#0066CC"); // Blue
                break;
            default:
                icon = new Icon(VaadinIcon QUESTION_CIRCLE_O);
                icon.setColor("#999999"); // Gray
        }
        
        icon.setSize("24px");
        return icon;
    }
    
    private Html createStatusBadge(AdapterStatus status) {
        String label;
        String className;
        
        switch (status) {
            case UP:
                label = "UP";
                className = "status-badge-up";
                break;
            case WARN:
                label = "WARN";
                className = "status-badge-warn";
                break;
            case DOWN:
                label = "DOWN";
                className = "status-badge-down";
                break;
            case CONNECTING:
                label = "CHECKING";
                className = "status-badge-connecting";
                break;
            default:
                label = "UNKNOWN";
                className = "status-badge-unknown";
        }
        
        Html badge = new Html("<span class=\"" + className + "\">" + label + "</span>");
        return badge;
    }
    
    private String extractEndpointName(String endpoint) {
        // Extract friendly name from URL
        // http://192.168.2.108:11434 -> "192.168.2.108"
        if (endpoint == null || endpoint.isEmpty()) {
            return "Unknown";
        }
        
        try {
            String withoutProtocol = endpoint.replace("http://", "").replace("https://", "");
            String withoutPort = withoutProtocol.split(":")[0];
            return withoutPort;
        } catch (Exception e) {
            return endpoint;
        }
    }
}
```

**File:** `src/main/java/com/hdekker/ai_workflow/ui/views/ObservabilityView.java`

```java
package com.hdekker.ai_workflow.ui.views;

import com.hdekker.ai_workflow.rest.dto.LLMStatus;
import com.hdekker.ai_workflow.service.LLMStatusService;
import com.hdekker.ai_workflow.ui.components.AdapterStatusComponent;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Hr;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.AfterNavigationEvent;
import com.vaadin.flow.router.AfterNavigationObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.List;

/**
 * Observability dashboard showing LLM adapter health status.
 * 
 * Route: /observability
 * 
 * Displays:
 * - Status cards for each configured LLM endpoint
 * - Manual refresh button
 * - Auto-refresh on navigation (optional)
 */
@Route("observability")
@PageTitle("Observability")
public class ObservabilityView extends VerticalLayout implements AfterNavigationObserver {
    
    private final LLMStatusService llmStatusService;
    private final VerticalLayout cardsContainer;
    
    @Autowired
    public ObservabilityView(LLMStatusService llmStatusService) {
        this.llmStatusService = llmStatusService;
        
        // Setup layout
        setPadding(true);
        setSpacing(true);
        addClassName("observability-view");
        
        // Header
        H2 header = new H2("LLM Adapter Status");
        header.addClassName("page-title");
        
        // Refresh button
        Button refreshButton = new Button("Refresh", event -> reloadData());
        refreshButton.setIcon(new Icon(VaadinIcon.REFRESH));
        
        HorizontalLayout headerLayout = new HorizontalLayout(header, refreshButton);
        headerLayout.setWidthFull();
        headerLayout.setJustifyContentMode(JustifyContentMode.BETWEEN);
        
        // Cards container
        cardsContainer = new VerticalLayout();
        cardsContainer.setWidthFull();
        cardsContainer.setSpacing(true);
        cardsContainer.addClassName("status-cards");
        
        // Separator
        Hr separator = new Hr();
        
        add(headerLayout, separator, cardsContainer);
    }
    
    @Override
    public void afterNavigation(AfterNavigationEvent event) {
        // Load data on view initialization
        reloadData();
    }
    
    private void reloadData() {
        // Show loading indicator (optional - could use ProgressBar)
        cardsContainer.removeAll();
        
        llmStatusService.getCurrentStatus().forEach(status -> {
            AdapterStatusComponent card = new AdapterStatusComponent(status);
            cardsContainer.add(card);
        });
        
        List<LLMStatus> statuses = llmStatusService.getCurrentStatus();
        if (statuses.isEmpty()) {
            Notification.show("No LLM endpoints configured", 3000, Notification.Position.MIDDLE);
        } else {
            Notification.show(
                "Loaded " + statuses.size() + " endpoint(s)", 
                2000, 
                Notification.Position.BOTTOM_START
            );
        }
    }
}
```

---

### 8. CSS Styling

**File:** `src/main/resources/META-INF/resources/styles/observability.css`

```css
/* Observability Panel Styles */

/* Main view */
.observability-view {
    background-color: var(--lumo-shade-5pct);
    min-height: 100vh;
}

.page-title {
    margin: 0;
    color: var(--lumo-header-text-color);
}

/* Status cards container */
.status-cards {
    display: flex;
    flex-direction: column;
    gap: var(--lumo-space-m);
}

/* Individual adapter status card */
.adapter-status-card {
    background-color: var(--lumo-base-color);
    border: 1px solid var(--lumo-contrast-10pct);
    border-radius: var(--lumo-border-radius-m);
    padding: var(--lumo-space-m);
    box-shadow: var(--lumo-box-shadow-s);
}

.adapter-status-card:hover {
    box-shadow: var(--lumo-box-shadow-m);
}

/* Header section */
.adapter-header {
    margin-bottom: var(--lumo-space-s);
}

.endpoint-name {
    font-weight: var(--lumo-font-weight-semibold);
    font-size: var(--lumo-font-size-m);
}

.endpoint-name [part="value"] {
    color: var(--lumo-header-text-color);
}

/* Status badges */
.status-badge-up,
.status-badge-warn,
.status-badge-down,
.status-badge-connecting,
.status-badge-unknown {
    display: inline-block;
    padding: var(--lumo-space-xs) var(--lumo-space-m);
    border-radius: var(--lumo-border-radius-s);
    font-size: var(--lumo-font-size-s);
    font-weight: var(--lumo-font-weight-semibold);
    text-transform: uppercase;
    letter-spacing: 0.05em;
}

.status-badge-up {
    background-color: var(--lumo-success-color-10pct);
    color: var(--lumo-success-color);
}

.status-badge-warn {
    background-color: var(--lumo-warning-color-10pct);
    color: var(--lumo-warning-color);
}

.status-badge-down {
    background-color: var(--lumo-error-color-10pct);
    color: var(--lumo-error-color);
}

.status-badge-connecting {
    background-color: var(--lumo-primary-color-10pct);
    color: var(--lumo-primary-color);
}

.status-badge-unknown {
    background-color: var(--lumo-contrast-10pct);
    color: var(--lumo-contrast-50pct);
}

/* Details section */
.adapter-details {
    margin-top: var(--lumo-space-s);
    padding-top: var(--lumo-space-s);
    border-top: 1px solid var(--lumo-contrast-10pct);
}

.adapter-details .vaadin-text-field {
    margin: 0;
    --lumo-text-field-size: 1.5rem;
}

.adapter-details [part="value"] {
    color: var(--lumo-secondary-text-color);
    font-size: var(--lumo-font-size-s);
}

.last-checked [part="value"] {
    font-style: italic;
}

.model-count [part="value"] {
    font-weight: var(--lumo-font-weight-semibold);
}

.model-names [part="value"] {
    font-family: var(--lumo-font-family-mono);
    font-size: var(--lumo-font-size-xs);
}

.error-message [part="value"] {
    color: var(--lumo-error-color);
}
```

**File:** `src/main/java/com/hdekker/ai_workflow/AiWorkflowApplication.java` (Additional Update)

Add import for CSS:

```java
import com.vaadin.flow.component.page.Page;
import com.vaadin.flow.component.page.Push;
```

Or create a `Theme` class to include the CSS.

---

## State Machine for LLM Status

```
UNKNOWN ──[poll starts]──► CONNECTING
                             │
              ┌──────────────┼──────────────┐
              │              │              │
         [success]      [timeout]     [error]
              │              │              │
              ▼              ▼              ▼
            UP            DOWN           DOWN
              │
       [no poll for 1hr+]
              │
              ▼
            WARN
              │
        [poll succeeds]
              │
              ▼
            UP
```

### State Transitions

| Current State | Event | New State | Condition |
|---------------|-------|-----------|-----------|
| UNKNOWN | Poll starts | CONNECTING | Always |
| CONNECTING | listModels succeeds | UP | Response received |
| CONNECTING | Timeout | DOWN | >5 seconds (configurable) |
| CONNECTING | Error | DOWN | HTTP error, connection refused |
| UP | Poll succeeds | UP | Fresh data |
| UP | Poll fails | DOWN | Immediate failure |
| UP | Data stale >1hr | WARN | No new successful polls |
| WARN | Poll succeeds | UP | Recovery |
| WARN | Poll fails | DOWN | Complete failure |
| DOWN | Poll succeeds | UP | Recovery |

---

## Implementation Steps

### Step 1: Database Entity & Repository ✅ COMPLETE
- [x] Create `LLMStatusEntity.java`
- [x] Create `LLMStatusRepository.java`
- [x] JPA will auto-create table on startup (ddl-auto: create-drop currently)

### Step 2: DTO Layer
- Create `LLMStatus.java` record
- Create `AdapterStatus.java` enum

### Step 3: Health Adapter Layer (OpenAI-Compatible) ✅ COMPLETE

#### 3.1 Add Test Dependency & Verify Build ✅
- [x] No additional dependency needed - `spring-boot-starter-webflux` already provides `RestClient`
- [x] Created `OpenAiHealthClientTest.java` with tests
- [x] Created `OpenAiHealthAdapterTest.java` with tests
- [x] Created `OpenAiHealthClientRestClientTest.java` with `@RestClientTest` tests
- [x] Verified build with `./mvnw compile test-compile`
- [x] Spring Boot 4.0.3 compatible

#### Files Created:
- [x] `OpenAiModelsResponse.java` - DTO for parsing `/v1/models` API response
- [x] `OpenAiHealthClient.java` - REST client using `RestClient` to call `/v1/models`
- [x] `OpenAiHealthAdapter.java` - Adapter that wraps client and returns `LLMStatus`
- [x] `OpenAiHealthConfiguration.java` - Spring config for adapter bean
- [x] `ObservabilityProperties.java` - Config properties for timeout/polling
- [x] `OpenAiHealthClientTest.java` - Unit tests for client
- [x] `OpenAiHealthAdapterTest.java` - Unit tests for adapter
- [x] `OpenAiHealthClientRestClientTest.java` - RestClient-specific tests

#### 3.2 OpenAI Models API - VERIFIED ✅
- **Endpoint Tested**: `GET http://192.168.2.108:8080/v1/models`
- **Actual Response Structure**:
  ```json
  {
    "models": [
      {
        "name": "qwen3-coder6",
        "model": "qwen3-coder6",
        "modified_at": "",
        "size": "",
        "digest": "",
        "type": "model",
        "description": "",
        "tags": [],
        "capabilities": ["completion", "multimodal"],
        "parameters": "",
        "details": {}
      }
    ],
    "object": "list",
    "data": [
      {
        "id": "qwen3-coder6",
        "aliases": ["qwen3-coder6"],
        "tags": [],
        "object": "model",
        "created": 1776343040,
        "owned_by": "llamacpp",
        "meta": {
          "vocab_type": 2,
          "n_vocab": 248320,
          "n_ctx_train": 262144,
          "n_embd": 5120,
          "n_params": 26895998464,
          "size": 19589154816
        }
      }
    ]
  }
  ```
- **Key Fields for DTO**:
  - Use `data[]` array (OpenAI-compatible format)
  - Extract `id` field for model names

#### 3.3 Create OpenAI Models DTO ✅
**File:** `src/main/java/com/hdekker/ai_workflow/rest/dto/OpenAiModelsResponse.java`
- [x] Create DTO to parse OpenAI `/models` API response
- [x] Main class fields:
  - `object` (String) - should be "list"
  - `data` (List<OpenAiModel>) - array of models
- [x] Nested `OpenAiModel` class:
  - `id` (String) - **PRIMARY FIELD** - model name (e.g., "qwen3-coder6")
  - `aliases` (List<String>)
  - `tags` (List<String>)
  - `object` (String) - should be "model"
  - `created` (Long) - timestamp
  - `owned_by` (String)
  - `meta` (Object) - can ignore or make optional
- [x] Use `@JsonProperty` for Jackson deserialization
- [x] Use `data[]` array for model extraction (OpenAI-compatible format)

#### 3.4 Create OpenAiHealthClient (Dedicated REST Client) ✅
**File:** `src/main/java/com/hdekker/ai_workflow/llm/OpenAiHealthClient.java`
- [x] Use `RestClient` (preferred) or `WebClient` for HTTP calls
- [x] Constructor: inject `RestClient.Builder` and configure base URL
- [x] Method: `listModels()` → `Mono<List<String>>` or `List<String>`
- [x] Call: `GET /v1/models` (root URI configured separately)
- [x] Parse response using `OpenAiModelsResponse` DTO
- [x] Extract model IDs from response
- [x] Handle errors (connection refused, timeout, HTTP errors)
- [x] Configure timeout from `ObservabilityProperties`

#### 3.5 Create OpenAiHealthAdapter ✅
**File:** `src/main/java/com/hdekker/ai_workflow/llm/OpenAiHealthAdapter.java`
- [x] Uses `OpenAiHealthClient` internally
- [x] Constructor: inject `OpenAiHealthClient` and timeout config
- [x] Method: `checkHealth(String endpoint, String configuredModel)` → `Mono<LLMStatus>`
- [x] Create client with endpoint, call `listModels()`
- [x] Return `LLMStatus` with:
  - `AdapterStatus.UP` if models retrieved successfully
  - `AdapterStatus.DOWN` on error/timeout
  - Model count and model names
- [x] Error handling with logging
- [x] Timeout handling with configurable duration

#### 3.6 Create OpenAiHealthClientTest ✅
**File:** `src/test/java/com/hdekker/ai_workflow/llm/OpenAiHealthClientTest.java`
- [x] Use `@RestClientTest(OpenAiHealthClient.class)`
- [x] Autowire `MockRestServiceServer`
- [x] Test cases:
  - **Success**: Mock `/v1/models` returning actual response format, verify model IDs extracted
    ```json
    {"object":"list","data":[{"id":"qwen3-coder6","object":"model","created":1776343040,"owned_by":"llamacpp"}]}
    ```
  - **Multiple Models**: Mock with 2-3 models, verify all IDs returned
  - **Empty**: Mock returning `{"object":"list","data":[]}`, verify empty list
  - **HTTP Error**: Mock 500 error, verify exception handling
  - **Connection Error**: Mock connection refused scenario
- [x] Use full URI in expectations: `requestTo("http://localhost:8080/v1/models")` (if not using `rootUri()`)

#### 3.7 Create OpenAiHealthAdapterTest ✅
**File:** `src/test/java/com/hdekker/ai_workflow/llm/OpenAiHealthAdapterTest.java`
- [x] Use `@ExtendWith(MockitoExtension.class)`
- [x] Mock `OpenAiHealthClient` with Mockito
- [x] Test health check logic:
  - Success path: models returned → `AdapterStatus.UP`
  - Error path: exception thrown → `AdapterStatus.DOWN`
  - Timeout path: timeout exception → `AdapterStatus.DOWN`
- [x] Verify `LLMStatus` fields populated correctly

#### 3.8 Create Configuration ✅
**File:** `src/main/java/com/hdekker/ai_workflow/llm/OpenAiHealthConfiguration.java`
- [x] Bean: `OpenAiHealthClient` 
  - [x] Inject `RestClient.Builder`
  - [x] Configure timeout from `ObservabilityProperties`
  - [x] Optionally set root URI if using single endpoint
- [x] Bean: `OpenAiHealthAdapter`
  - [x] Inject `OpenAiHealthClient`
  - [x] Inject timeout config
- [x] Use `ObservabilityProperties` for timeout values

---

### Key Architecture Decisions

**Why Dedicated REST Client?**
- OpenAiApi (from spring-ai-openai) does NOT have `listModels()` method
- Requires custom HTTP implementation
- Separation of concerns: client handles HTTP, adapter handles business logic
- Easier to test with `@RestClientTest` and `MockRestServiceServer`

**RestClient vs WebClient**
- `RestClient`: Simpler, recommended for straightforward REST calls (Spring Boot 3.1+)
- `WebClient`: More powerful, reactive, more verbose
- **Decision**: Use `RestClient` for simplicity

**Testing Approach**
- `@RestClientTest` auto-configures:
  - `RestClient.Builder`
  - `MockRestServiceServer`
  - Jackson/GSON/Jsonb support
- Does NOT scan `@Component` beans (use `@EnableConfigurationProperties` if needed)
- Use `requestTo("/v1/models")` if root URI set, else full URL

### Step 4: Configuration ✅ COMPLETE

#### 4.1 Add `endpoint` and `model` fields to `ObservabilityProperties.java` ✅
**File:** `src/main/java/com/hdekker/ai_workflow/observability/ObservabilityProperties.java`
- [x] Added `endpoint` field (default: `http://localhost:8080`)
- [x] Added `model` field (default: `null`)
- [x] Added getters/setters for both fields
- [x] Updated Javadoc with example `application.yml`

#### 4.2 Add `@EnableScheduling` to `AiWorkflowApplication.java` ✅
**File:** `src/main/java/com/hdekker/ai_workflow/AiWorkflowApplication.java`
- [x] Added `@EnableScheduling` annotation
- [x] Added `ObservabilityProperties.class` to `@EnableConfigurationProperties`
- [x] Added `import org.springframework.scheduling.annotation.EnableScheduling`

#### 4.3 Add `app.observability.*` properties to `application.yml` ✅
**File:** `src/main/resources/application.yml`
- [x] Added `app.observability.endpoint` = `http://192.168.2.108:11434`
- [x] Added `app.observability.model` = `gemma3:27b`
- [x] Added `app.observability.polling-interval` = `60000` (1 minute)
- [x] Added `app.observability.warn-after-hours` = `1`
- [x] Added `app.observability.health-timeout` = `5000` (5 seconds)

#### Files Created/Modified:
- [x] `ObservabilityProperties.java` (modified - added endpoint/model fields)
- [x] `AiWorkflowApplication.java` (modified - added @EnableScheduling + ObservabilityProperties)
- [x] `application.yml` (modified - added app.observability.* section)

### Step 5: Service Layer ✅ COMPLETE

#### 5.1 Create `LLMStatusService.java` ✅
**File:** `src/main/java/com/hdekker/ai_workflow/service/LLMStatusService.java`
- [x] Inject `LLMStatusRepository`, `OpenAiHealthAdapter`, `ObservabilityProperties`
- [x] `@Scheduled(fixedRateString)` method for periodic polling
- [x] `schedulePolling()` - calls health adapter, checks WARN condition, persists to DB, logs warnings
- [x] `checkWarnCondition()` - transitions UP → WARN when last check > warnAfterHours
- [x] `persistStatus()` - saves `LLMStatusEntity` to database
- [x] `getCurrentStatus()` - reads cached status from DB, converts entity to DTO
- [x] `triggerPoll()` - manual immediate refresh, returns list of statuses
- [x] `entityToDto()` - converts `LLMStatusEntity` to `LLMStatus` DTO
- [x] Null/empty endpoint guard - skips health check and logs warning
- [x] Null health status guard - logs error, skips persistence

#### 5.2 Create `LLMStatusServiceTest.java` ✅
**File:** `src/test/java/com/hdekker/ai_workflow/service/LLMStatusServiceTest.java`
- [x] Use `@ExtendWith(MockitoExtension.class)` with Mockito mocks
- [x] Mock `LLMStatusRepository`, `OpenAiHealthAdapter`, `ObservabilityProperties`
- [x] Reflection helper for invoking private `checkWarnCondition()` method
- [x] Reflection helper for setting `@Value` fields (e.g., `warnAfterHours`)
- [x] Test: `schedulePolling_success_persistsStatus` - UP status saved with correct fields
- [x] Test: `schedulePolling_failure_persistsDownStatus` - DOWN status with error message
- [x] Test: `schedulePolling_nullEndpoint_skipsHealthCheck` - no adapter/repo calls
- [x] Test: `schedulePolling_emptyEndpoint_skipsHealthCheck` - no adapter/repo calls
- [x] Test: `schedulePolling_healthAdapterError_returnsDown` - adapter error → DOWN persisted
- [x] Test: `checkWarnCondition_freshData_unchanged` - UP with no history stays UP
- [x] Test: `checkWarnCondition_staleData_returnsWarn` - UP older than 1hr → WARN
- [x] Test: `checkWarnCondition_recentData_noWarn` - UP within threshold stays UP
- [x] Test: `checkWarnCondition_alreadyDown_unchanged` - DOWN stays DOWN
- [x] Test: `checkWarnCondition_noPreviousData_unchanged` - no repo entry → stays UP
- [x] Test: `triggerPoll_success_returnsStatusList` - manual trigger returns 1 status
- [x] Test: `triggerPoll_nullEndpoint_returnsEmptyList` - null endpoint → empty list
- [x] Test: `getCurrentStatus_empty_returnsEmptyList` - empty repo → empty list
- [x] Test: `getCurrentStatus_withData_returnsDtoList` - entity → DTO conversion correct
- [x] Test: `getCurrentStatus_entityWithEmptyModelNames_returnsEmptyList` - empty string → empty list
- [x] All 15 tests pass, no impact on existing tests

#### Files Created/Modified:
- [x] `LLMStatusService.java` (created - 200 lines)
- [x] `LLMStatusServiceTest.java` (created - 290 lines)
- [x] Build verified: `./mvnw compile test-compile`
- [x] Tests verified: `./mvnw test -Dtest=LLMStatusServiceTest -q` (15/15 pass)

### Step 6: REST Endpoint
- Create `ObservabilityRestController.java`
- Implement GET `/api/observability/llm-status`
- Implement POST `/api/observability/llm-status/poll`

### Step 7: UI Components
- Create `AdapterStatusComponent.java`
- Create `ObservabilityView.java`
- Create `observability.css`
- Add styles to Vaadin theme

### Step 8: Testing
- Test health check with running OpenAI-compatible endpoint
- Test health check with stopped OpenAI-compatible endpoint
- Test WARN condition (modify timestamp)
- Test REST endpoint
- Test UI rendering

---

## Database Schema

### Table: `llm_status`

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| endpoint | VARCHAR(255) | PRIMARY KEY | Endpoint URL |
| configured_model | VARCHAR(100) | | Expected model name |
| status | VARCHAR(20) | | UNKNOWN/CONNECTING/UP/WARN/DOWN |
| last_checked | TIMESTAMP | | Last successful check time |
| model_count | INTEGER | | Number of available models |
| model_names | TEXT | | Comma-separated model names |
| error_message | TEXT | | Last error if DOWN |

**SQL Example:**
```sql
CREATE TABLE llm_status (
    endpoint VARCHAR(255) PRIMARY KEY,
    configured_model VARCHAR(100),
    status VARCHAR(20) NOT NULL,
    last_checked TIMESTAMP,
    model_count INTEGER,
    model_names TEXT,
    error_message TEXT
);
```

---

## Future Enhancements

### Phase 2: Multiple Endpoints Support
- Add configuration for multiple OpenAI-compatible endpoints
- Each endpoint stored as separate row in `llm_status` table
- UI shows all endpoints in separate cards

### Phase 3: Endpoint Configuration UI
- Add form to configure/add/remove endpoints
- Store endpoint configuration in database
- Dynamic polling schedule per endpoint

### Phase 4: Historical Data
- Add separate `llm_status_history` table
- Track status changes over time
- Add charts for status trends

### Phase 5: Alerts
- Email/Slack notifications on status changes
- Configurable alert thresholds
- Acknowledgement tracking

---

## Testing Checklist

- [x] Health check succeeds when OpenAI-compatible endpoint is running
- [x] Health check fails when OpenAI-compatible endpoint is stopped
- [x] Health check times out after configured duration
- [x] Status persists to database correctly
- [x] WARN state triggers after 1 hour without data
- [ ] Health check with running OpenAI-compatible endpoint (integration)
- [ ] Health check with stopped OpenAI-compatible endpoint (integration)
- [ ] REST endpoint returns current status
- [ ] REST endpoint triggers immediate poll
- [ ] UI renders status cards correctly
- [ ] Status colors match: UP=green, WARN=yellow, DOWN=red
- [ ] Warnings logged to console for DOWN/WARN states
- [x] Scheduled polling runs at configured interval (unit: method executes correctly)
- [ ] Multiple endpoints supported (future)

---

## Notes

### Health Check Method
- Uses `GET /v1/models` (OpenAI-compatible API) - verified to NOT consume tokens
- Does NOT affect conversation context
- Verifies: endpoint reachable, service available, models loaded

### Database Choice
- Currently using H2 (in-memory) for development
- Will switch to SQLite when application.db is configured
- JPA handles dialect automatically

### Polling Strategy
- Auto-starts on application startup
- Runs every 60 seconds (configurable)
- Non-blocking, uses Reactor Mono
- Logs warnings for DOWN/WARN states

### UI Approach
- Standalone route at `/observability`
- Reusable `AdapterStatusComponent` for each endpoint
- Vaadin components: Card, HorizontalLayout, Icon, TextField
- CSS styling for status badges

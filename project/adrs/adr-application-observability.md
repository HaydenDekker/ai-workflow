# ADR: Application Observability with LLM Health Monitoring

## Context

Modern AI applications rely on external LLM services that can be:
- Local (Ollama on same machine)
- Remote (Ollama on another machine)
- Multiple endpoints (small/fast LLM for quick tasks, large/slow LLM for complex reasoning)

The current architecture has no visibility into LLM service health. When an endpoint becomes unavailable:
- Failures happen at request time
- No proactive alerting
- No dashboard visibility
- Debugging requires manual curl tests

We need an observability layer that provides:
1. **Real-time health status** of all configured LLM endpoints
2. **Non-intrusive monitoring** that doesn't consume tokens or affect conversation context
3. **Persistent state** for historical tracking and debugging
4. **Visual dashboard** for operational awareness

## Decision

We will implement an **LLM Observability Subsystem** following hexagonal architecture principles:

### Core Principles

1. **Health-First Monitoring**: Use `listModels()` API calls instead of test prompts
   - No token consumption
   - No context interference
   - Verifies endpoint reachability, service availability, and model configuration

2. **Hexagonal Architecture**: Clear separation between core logic, adapters, and infrastructure

3. **Multi-Endpoint Support**: Design for multiple LLM endpoints from the start
   - Small/fast LLM on edge device
   - Large/slow LLM on server
   - Each endpoint monitored independently

4. **Status Lifecycle**: Three-state model with time-based transitions
   - **UP** (green): Recently checked and healthy
   - **WARN** (yellow): Previously healthy, no response for configured threshold (1 hour)
   - **DOWN** (red): Check failed (timeout, connection refused, error)

5. **Persistence Layer**: SQLite database table for status history
   - Survives application restarts
   - Enables historical analysis
   - Minimal schema overhead

## Architecture

### Hexagonal Architecture Layout

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    Application Layer (Use Cases)                        │
│                                                                         │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │  LLMStatusService                                               │   │
│  │  - Orchestrates health checking workflow                        │   │
│  │  - Manages scheduled polling                                    │   │
│  │  - Applies business logic (WARN threshold)                      │   │
│  │  - Coordinates persistence                                        │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│                                                                         │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │  ObservabilityProperties                                        │   │
│  │  - Polling interval configuration                               │   │
│  │  - WARN threshold configuration                                 │   │
│  │  - Health check timeout configuration                           │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│                                                                         │
└──────────────────────────┬──────────────────────────────────────────────┘
                           │
                           │ Uses
                           ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                      Domain Layer (Core Logic)                          │
│                                                                         │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │  LLMStatus (Record)                                             │   │
│  │  - endpoint: String                                             │   │
│  │  - status: AdapterStatus (UNKNOWN, CONNECTING, UP, WARN, DOWN) │   │
│  │  - lastChecked: LocalDateTime                                   │   │
│  │  - modelCount: Integer                                          │   │
│  │  - errorMessage: String                                         │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│                                                                         │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │  LLMStatusEntity (JPA Entity)                                   │   │
│  │  - Database mapping for persistence                             │   │
│  │  - Primary key: endpoint                                        │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│                                                                         │
└──────────────────────────┬──────────────────────────────────────────────┘
                           │
                           │ Interface (Port)
                           ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                   Infrastructure Adapters (Ports)                       │
│                                                                         │
│  ┌──────────────────────────┐   ┌─────────────────────────────────┐    │
│  │  HealthAdapterPort       │   │  StatusRepositoryPort          │    │
│  │  (Interface)             │   │  (JPA Repository)              │    │
│  │  - checkHealth()         │   │  - save()                       │    │
│  │  - supportsEndpoint()    │   │  - findByEndpoint()             │    │
│  └──────────────────────────┘   │  - findAll()                    │    │
│                                 └─────────────────────────────────┘    │
│                                                                         │
└──────────────────────────┬──────────────────────────────────────────────┘
                           │
                           │ Implementation
                           ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                     Adapter Layer (Drivers)                             │
│                                                                         │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │  OllamaHealthAdapter                                            │   │
│  │  - Implements HealthAdapterPort                                 │   │
│  │  - Uses OllamaApi.listModels() for health checks                │   │
│  │  - Handles timeouts and errors                                  │   │
│  │  - Returns LLMStatus with detailed information                  │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│                                                                         │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │  Future: OpenAIHealthAdapter                                    │   │
│  │  - Implements HealthAdapterPort                                 │   │
│  │  - Uses OpenAI API health endpoints                             │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│                                                                         │
└──────────────────────────┬──────────────────────────────────────────────┘
                           │
                           │ Exposes
                           ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                      Presentation Layer (UI/REST)                       │
│                                                                         │
│  ┌──────────────────────────┐   ┌─────────────────────────────────┐    │
│  │  ObservabilityRestController │  ObservabilityView              │    │
│  │  - GET /api/observability/   │  - Route: /observability        │    │
│  │    llm-status                │  - AdapterStatusComponent cards │    │
│  │  - POST /api/observability/  │  - Real-time status display     │    │
│  │    llm-status/poll           │  - Manual refresh button        │    │
│  └──────────────────────────┘   └─────────────────────────────────┘    │
│                                                                         │
│  Vaadin Components Used:                                                │
│  - Card (container)                                                     │
│  - HorizontalLayout / VerticalLayout                                    │
│  - Icon (status indicators)                                             │
│  - TextField (details)                                                  │
│  - Button (refresh)                                                     │
│  - Notification (feedback)                                              │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

### Component Responsibilities

| Component | Layer | Responsibility |
|-----------|-------|----------------|
| `LLMStatusService` | Application | Orchestrates health checking workflow, scheduled polling, business logic |
| `OllamaHealthAdapter` | Adapter | Implements health check using Ollama API |
| `LLMStatus` | Domain | Immutable status data record |
| `LLMStatusEntity` | Domain | JPA entity for persistence |
| `LLMStatusRepository` | Infrastructure | JPA repository interface |
| `ObservabilityRestController` | Presentation | REST API for status retrieval |
| `ObservabilityView` | Presentation | Vaadin UI dashboard |
| `AdapterStatusComponent` | Presentation | Reusable status card component |
| `ObservabilityProperties` | Application | Configuration properties |

## Consequences

### Positive

1. **Operational Visibility**: Real-time dashboard shows which LLM endpoints are healthy
2. **Proactive Alerting**: Logs warn when endpoints become unavailable
3. **Non-Intrusive Monitoring**: Health checks don't consume tokens or affect context
4. **Extensible Design**: Hexagonal architecture allows new adapter types (OpenAI, Anthropic, etc.)
5. **Persistent State**: Database history enables trend analysis and debugging
6. **Configurable Thresholds**: Polling interval, WARN threshold, timeout all configurable

### Negative

1. **Additional Complexity**: New subsystem with multiple layers and components
2. **Resource Overhead**: Background polling consumes minimal but non-zero resources
3. **Database Schema**: New table required (auto-created by JPA)
4. **Configuration**: New properties to manage in `application.yml`

### Neutral

1. **Learning Curve**: New developers must understand observability patterns
2. **Testing**: Requires both unit tests (health adapter) and integration tests (end-to-end polling)

## Naming Conventions

### Package Structure

```
com.hdekker.ai_workflow
├── database.llmstatus          # JPA entities and repositories
│   ├── LLMStatusEntity.java
│   └── LLMStatusRepository.java
├── observability               # Application configuration
│   ├── ObservabilityProperties.java
│   └── OllamaHealthConfiguration.java
├── ollama                      # Adapter implementations
│   └── OllamaHealthAdapter.java
├── rest.dto                    # DTOs for API layer
│   ├── LLMStatus.java
│   └── AdapterStatus.java
├── rest                        # REST controllers
│   └── ObservabilityRestController.java
├── service                     # Application services
│   └── LLMStatusService.java
└── ui
    ├── components              # Reusable UI components
    │   └── AdapterStatusComponent.java
    └── views                   # Vaadin views
        └── ObservabilityView.java
```

### Class Naming

| Pattern | Example | Purpose |
|---------|---------|---------|
| `*Status` | `LLMStatus`, `AdapterStatus` | Data records (DTOs, domain objects) |
| `*Entity` | `LLMStatusEntity` | JPA entities |
| `*Repository` | `LLMStatusRepository` | JPA repositories |
| `*Service` | `LLMStatusService` | Application services |
| `*Adapter` | `OllamaHealthAdapter` | Infrastructure adapters |
| `*Configuration` | `OllamaHealthConfiguration` | Spring configuration |
| `*Properties` | `ObservabilityProperties` | Configuration properties |
| `*Controller` | `ObservabilityRestController` | REST controllers |
| `*View` | `ObservabilityView` | Vaadin views |
| `*Component` | `AdapterStatusComponent` | Reusable UI components |

### Status State Names

| State | Color | Meaning |
|-------|-------|---------|
| `UNKNOWN` | Gray | No data yet (initial state) |
| `CONNECTING` | Blue | Currently checking (transient) |
| `UP` | Green | Healthy, recently checked |
| `WARN` | Yellow | Previously healthy, stale data (>1 hour) |
| `DOWN` | Red | Check failed (timeout, error, unreachable) |

## Implementation Approach

### Phase 1: Core Infrastructure (Priority: High)

1. **Database Layer**: Create `LLMStatusEntity` and `LLMStatusRepository`
2. **Domain Model**: Create `LLMStatus` record and `AdapterStatus` enum
3. **Health Adapter**: Implement `OllamaHealthAdapter` using `listModels()`
4. **Configuration**: Create `ObservabilityProperties` and `OllamaHealthConfiguration`

### Phase 2: Service Layer (Priority: High)

5. **Service**: Implement `LLMStatusService` with scheduled polling
6. **REST API**: Create `ObservabilityRestController`
7. **Enable Scheduling**: Add `@EnableScheduling` to main application

### Phase 3: UI Layer (Priority: Medium)

8. **UI Components**: Create `AdapterStatusComponent`
9. **Dashboard View**: Create `ObservabilityView`
10. **Styling**: Create `observability.css` with status badge styles

### Phase 4: Testing & Polish (Priority: Medium)

11. **Unit Tests**: Test health adapter logic
12. **Integration Tests**: Test end-to-end polling and persistence
13. **Documentation**: Update README and operational guides

## Alternatives Considered

### Alternative 1: Test Prompt-Based Health Checks

Use actual chat prompts to verify LLM availability:

```java
chatClient.call("Are you alive?")
```

**Rejected**: 
- Consumes tokens
- Affects conversation context
- Slower than API-only checks
- May trigger rate limits

### Alternative 2: Spring Boot Actuator Health Indicators

Implement custom `HealthIndicator` beans:

```java
@Component
public class OllamaHealthIndicator implements HealthIndicator {
    // ...
}
```

**Rejected for primary approach**:
- Actuator health is for infrastructure monitoring (/actuator/health)
- Less suitable for dashboard visualization
- Doesn't provide model count/details
- Can be added later as complementary feature

### Alternative 3: In-Memory Cache Only

Store status in memory without database persistence:

```java
private final Map<String, LLMStatus> cache = new ConcurrentHashMap<>();
```

**Rejected**:
- Lost on application restart
- No historical analysis
- Can't debug intermittent issues
- Database is already part of architecture (H2/SQLite)

### Alternative 4: Event-Driven Status Updates

Push status changes via WebSocket to UI:

```java
@MessageMapping("/llm-status")
public void sendStatus(LLMStatus status) { ... }
```

**Rejected for initial implementation**:
- Added complexity (WebSocket infrastructure)
- Overkill for current requirements
- Polling is sufficient for 1-minute intervals
- Can be added later if real-time updates needed

## Technical Specifications

### Health Check Method

**Chosen Method**: `OllamaApi.listModels()`

**Why**:
- No token consumption
- No context interference
- Verifies endpoint, service, AND model configuration
- Fast response time (<1 second typically)
- Already used in existing `OllamaInstanceAdapterUtils`

**Alternative Methods Considered**:
- `/api/version` - Too lightweight, doesn't verify models
- `/api/ps` - Only shows running models, not all available
- Direct HTTP ping - Doesn't verify Ollama service, just TCP

### Database Schema

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

**Rationale**:
- Single table, minimal schema
- Primary key on endpoint enables upserts
- Text fields for model names and errors (variable length)
- JPA auto-creates with `ddl-auto: create-drop`

### Polling Strategy

**Default Configuration**:
- Interval: 60 seconds
- WARN threshold: 1 hour
- Timeout: 5 seconds

**Rationale**:
- 60s: Frequent enough for operational awareness, infrequent enough to avoid overhead
- 1h: Grace period for temporary network issues before marking WARN
- 5s: Long enough for slow networks, short enough to detect failures quickly

## Open Questions

1. **Multi-Endpoint Configuration**: How to configure multiple endpoints?
   
   **Current Approach**: Single endpoint from `app.ai.endpoint`
   
   **Future Enhancement**: Configuration table with CRUD REST API for multiple endpoints

2. **Historical Retention**: How long to keep status history?
   
   **Current Approach**: Latest status only (overwrite on each poll)
   
   **Future Enhancement**: Separate `llm_status_history` table with retention policy

3. **Alerting**: How to notify operators of status changes?
   
   **Current Approach**: Log warnings only
   
   **Future Enhancement**: Email/Slack notifications, configurable thresholds

4. **Dashboard Refresh**: Should UI auto-refresh or manual only?
   
   **Current Approach**: Manual refresh button, load on navigation
   
   **Future Enhancement**: Auto-refresh every 30 seconds via polling

## References

- Existing Scanner Architecture: `docs/dpr-scanner-concept.md`, `docs/dpr-agent-scanner-relationship.md`
- Spring AI Documentation: https://docs.spring.io/spring-ai/reference/
- Ollama API Documentation: https://github.com/ollama/ollama/blob/main/docs/api.md
- Vaadin Components: https://vaadin.com/docs/latest/components

---

**Author**: AI Workflow Team  
**Date**: 2026-04-13  
**Last Updated**: 2026-04-13

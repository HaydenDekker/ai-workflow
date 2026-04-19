# ADR-002: Vaadin/Hilla UI Components

## Date

2026-04-19

## Context

The application needs a visual interface for two distinct purposes:

1. **Agent Management Dashboard** — View and manage the configured AI agents, their status, and metadata.
2. **LLM Observability Dashboard** — Monitor the health status of configured LLM endpoints in real-time.

Existing options for building the frontend:
- **React + Vite** (standalone SPA) — Requires separate build pipeline, CORS configuration, deployment separation
- **Plain HTML/JS + Thymeleaf** — Server-rendered pages, limited interactivity, manual state management
- **Vaadin + Hilla** — Full-stack Java framework, server-rendered with WebSocket push, zero CORS, single deployment artifact

Requirements:
1. **Single deployment artifact** (fat JAR) — no separate frontend build/deploy step
2. **Real-time updates** — Agent status changes and LLM health polling should reflect in the UI without manual refresh
3. **Type safety** — Java backend and frontend share the same DTOs, no serialization mismatches
4. **Rapid development** — Pre-built UI components (grids, cards, layouts, dialogs)
5. **Minimal JavaScript** — Developers should be able to build the entire UI in Java

## Decision

We will use **Vaadin 25 with Hilla** for all UI components. The UI lives inside the Spring Boot application as part of the same JAR. Views are Java classes annotated with `@Route`, components are reusable Java classes, and styling uses CSS in the Vaadin theme folder.

### Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    Spring Boot Application (Fat JAR)                    │
│                                                                         │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │                    Vaadin / Hilla Layer                         │   │
│  │                                                                 │   │
│  │  ┌──────────────────┐    ┌──────────────────────────────┐      │   │
│  │  │  Views           │    │  UI Components               │      │   │
│  │  │                  │    │                              │      │   │
│  │  │  AgentListView   │    │  (Reusable widgets)          │      │   │
│  │  │  Route: /agents  │    │                              │      │   │
│  │  │                  │    │  AdapterStatusComponent      │      │   │
│  │  │  ObservabilityView│   │  (Status cards)              │      │   │
│  │  │  Route: /observ- │    │                              │      │   │
│  │  │  ability         │    │  (Card, Layout, Icon,        │      │   │
│  │  │                  │    │   TextField, Button)         │      │   │
│  │  └────────┬─────────┘    └──────────────┬───────────────┘      │   │
│  │           │                             │                       │   │
│  │           │  Injected via @Autowired    │  Injected via         │   │
│  │           ▼                             │  constructor          │   │
│  └─────────────────────────────────────────┼───────────────────────┘   │
│                                           │                            │
└──────────────────────────┬────────────────┼────────────────────────────┘
                           │                │
                           │                │
                           ▼                ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                    Application Layer (Services)                         │
│                                                                         │
│  ┌──────────────────────────────┐    ┌──────────────────────────────┐  │
│  │  AgentInfoService            │    │  LLMStatusService            │  │
│  │  - getAllAgentInfos()        │    │  - getCurrentStatus()        │  │
│  │  - Reactive Flux<>           │    │  - triggerPoll()             │  │
│  │  - Reads from registry       │    │  - Scheduled polling         │  │
│  └──────────────────────────────┘    └──────────────────────────────┘  │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

### Component Structure

```
src/main/java/com/hdekker/ai_workflow/
├── ui/
│   ├── components/
│   │   └── AdapterStatusComponent.java   # Reusable status card
│   ├── service/
│   │   └── AgentInfoService.java         # UI service layer
│   └── views/
│       ├── AgentListView.java            # Agent management dashboard
│       └── ObservabilityView.java        # LLM health dashboard
├── rest/
│   └── dto/
│       ├── AgentInfo.java                # Shared DTO (also used by REST)
│       └── LLMStatus.java                # Shared DTO (also used by REST)
└── service/
    ├── AgentInfoService.java             # Backend service
    └── LLMStatusService.java             # Backend service

src/main/frontend/themes/default/
└── styles.css                            # Vaadin theme CSS
```

### Code Examples

**View — AgentListView**

```java
@Route("agents")
@PageTitle("Agent List")
public class AgentListView extends VerticalLayout
        implements AfterNavigationObserver {

    private final Grid<AgentInfo> grid;
    private final AgentInfoService agentInfoService;
    private final ProgressBar loadingIndicator;

    @Autowired
    public AgentListView(AgentInfoService agentInfoService) {
        this.agentInfoService = agentInfoService;
        initLayout();
    }

    @Override
    public void afterNavigation(AfterNavigationEvent event) {
        reloadData();  // Auto-load on view navigation
    }

    private void reloadData() {
        showLoading(true);
        agentInfoService.getAllAgentInfos()
            .doFinally(signal -> grid.getUI().get().access(() -> showLoading(false)))
            .subscribe(
                agentInfos -> grid.getUI().get().access(() -> updateGrid(agentInfos)),
                error -> grid.getUI().get().access(() -> {
                    Notification.show("Error: " + error.getMessage());
                    showLoading(false);
                })
            );
    }
}
```

**View — ObservabilityView**

```java
@Route("observability")
@PageTitle("Observability")
public class ObservabilityView extends VerticalLayout
        implements AfterNavigationObserver {

    private final LLMStatusService llmStatusService;
    private final VerticalLayout cardsContainer;
    private ScheduledExecutorService viewRefreshScheduler;

    @Override
    public void afterNavigation(AfterNavigationEvent event) {
        startViewAutoRefresh();  // 30-second refresh cycle
        loadStatusCards();
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        stopViewAutoRefresh();  // Prevent memory leaks
        super.onDetach(detachEvent);
    }
}
```

**Reusable Component — AdapterStatusComponent**

```java
public class AdapterStatusComponent extends HorizontalLayout {

    private final LLMStatus status;
    private final LLMStatusService service;
    private Icon statusIcon;
    private Div statusBadge;

    public AdapterStatusComponent(LLMStatus status, LLMStatusService service) {
        this.status = status;
        this.service = service;
        initLayout();
        updateDisplay();
        startAutoRefresh();
    }

    private void applyStatusStyles(AdapterStatus adapterStatus) {
        switch (adapterStatus) {
            case UP:
                statusIcon.setIcon(VaadinIcon.CHECK_CIRCLE_O);
                statusIcon.setColor("var(--lumo-success-color)");
                break;
            case DOWN:
                statusIcon.setIcon(VaadinIcon.CLOSE_CIRCLE_O);
                statusIcon.setColor("var(--lumo-error-color)");
                break;
            // ... other states
        }
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        stopAutoRefresh();  // Lifecycle-aware cleanup
        super.onDetach(detachEvent);
    }
}
```

### Configuration

**application.yml**

```yaml
vaadin:
  launch-browser: true
  allowed-packages: com.vaadin,org.vaadin,com.hdekker
```

**pom.xml** (already present, no changes needed)

```xml
<dependency>
    <groupId>com.vaadin</groupId>
    <artifactId>vaadin-spring-boot-starter</artifactId>
</dependency>
<dependency>
    <groupId>com.vaadin</groupId>
    <artifactId>hilla-spring-boot-starter</artifactId>
</dependency>
```

### Styling

CSS lives in the Vaadin theme folder and is automatically bundled with the JAR:

```css
/* src/main/frontend/themes/default/styles.css */

.adapter-status-card {
    background-color: var(--lumo-base-color);
    border: 1px solid var(--lumo-contrast-10pct);
    border-radius: var(--lumo-border-radius-m);
    padding: var(--lumo-space-m);
    box-shadow: var(--lumo-box-shadow-s);
    transition: box-shadow 0.2s ease;
}

.adapter-status-card:hover {
    box-shadow: var(--lumo-box-shadow-m);
}

.status-badge-up {
    background-color: var(--lumo-success-color-10pct);
    color: var(--lumo-success-color);
}

.status-badge-down {
    background-color: var(--lumo-error-color-10pct);
    color: var(--lumo-error-color);
}

@media (max-width: 768px) {
    .adapter-status-card {
        flex-direction: column;
        gap: var(--lumo-space-s);
    }
}
```

### Testing Strategy

Three tiers of testing:

| Test Type | Approach | Example |
|-----------|----------|---------|
| **Unit** | Mockito mocks for services | `AgentListViewTest` verifies service injection |
| **View Init** | Verify layout, grid columns, button wiring | `AgentListViewTest` checks column headers |
| **E2E** | Playwright with real Chromium browser | `agents.spec.ts`, `observability.spec.ts` |

```java
@SpringBootTest
class AgentListViewTest {

    @Autowired
    private AgentInfoService agentInfoService;

    @Test
    void viewInjectsService() {
        AgentListView view = new AgentListView(agentInfoService);
        assertNotNull(view);
    }
}
```

```typescript
// tests/e2e/observability.spec.ts
test('page loads with correct title', async ({ page }) => {
  await page.goto('/observability');
  await expect(page).toHaveTitle(/Observability/i);
});

test('status cards are rendered', async ({ page }) => {
  await page.goto('/observability');
  await page.waitForTimeout(5000); // health check
  const card = page.locator('.adapter-status-card');
  await expect(card).toHaveCount(1);
});
```

E2E tests are in `tests/e2e/`, configured via `playwright.config.ts`. Global setup starts the Spring Boot dev server; global teardown stops it. Run with `npm run test:e2e`.

## See Also

- [ADR-001: REST Adapters](adr-rest-adapters.md) — REST API layer that the UI does not depend on (services are injected directly)
- [ADR: Application Observability with LLM Health Monitoring](adr-application-observability.md) — Backend observability service that the ObservabilityView displays
- [ADR: Hilla Setup Guide](adr-hilla-setup-guide.md) — Hilla configuration (available but not actively used for current UI)

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
│  │           │  Injected via @Autowired    │  Consumer callbacks   │   │
│  │           │  (owns service access)      │  (pure UI only)       │   │
│  │           ▼                             │                       │   │
│  └─────────────────────────────────────────┤                       │   │
│                                           │                        │   │
└──────────────────────────┬────────────────┤───────────────────────┘   │
                           │                 │                            │
                           │  Mono<>         │  No service dependency     │
                           ▼                 ▼                            │
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

**Service Access Rules**:
- **Views** own `AgentInfoService` and `LLMStatusService` — they inject the services, own the reactive chains, and control `UI.access()` wrapping
- **Components** never access services — they accept data via constructors and communicate via `Consumer` callbacks
- **Components** can accept data objects (e.g., `AgentInfo`) for display, but must not call service methods
- **Views** coordinate between component callbacks and service calls, ensuring all Vaadin updates happen on the UI thread

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

**Reusable Component — Pure UI (no service dependency)**

Components must never access services. They accept data for display and communicate via callbacks.

```java
public class AdapterStatusComponent extends HorizontalLayout {

    private final LLMStatus status;
    private Icon statusIcon;
    private Div statusBadge;

    public AdapterStatusComponent(LLMStatus status) {
        // ✅ Only accepts data, no service injection
        this.status = status;
        initLayout();
        updateDisplay();
    }

    // View owns the LLMStatusService and scheduled refresh;
    // component only displays the data it receives.
    
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
        super.onDetach(detachEvent);
    }
}
```

**View — coordinates component and service**

```java
public class ObservabilityView extends VerticalLayout {
    private final LLMStatusService llmStatusService;
    private final AdapterStatusComponent statusBadge;
    private ScheduledExecutorService viewRefreshScheduler;

    @Autowired
    public ObservabilityView(LLMStatusService llmStatusService) {
        this.llmStatusService = llmStatusService;
        this.statusBadge = new AdapterStatusComponent(null);  // ✅ no service in component
        initLayout();
    }

    @Override
    public void afterNavigation(AfterNavigationEvent event) {
        // ✅ View owns service access and threading
        llmStatusService.triggerPoll().subscribe(
            status -> getUI().ifPresent(ui -> ui.access(() -> {
                statusBadge = new AdapterStatusComponent(status);
                cardsContainer.add(statusBadge);
            })),
            err -> getUI().ifPresent(ui -> ui.access(() -> showNotification(err)))
        );
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        viewRefreshScheduler.shutdownNow();
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

### Component Building Principles

#### 0. Service Access Boundary

**Components must never access services directly. Views own service access and coordinate all component state.**

This rule prevents threading bugs, reactive chain leaks, and testing complexity. The service layer lives in `ui/service/` and is injected into views, not components.

```java
// ❌ WRONG — component owns service (violates ADR-002)
public class AgentDetailDialog extends Dialog {
    private final AgentInfoService agentInfoService;  // ❌ component cannot access service
    
    private void handleDelete() {
        agentInfoService.deleteAgent(id).subscribe(...);  // ❌ reactive chain in component
    }
}

// ✅ CORRECT — view owns service, component uses callbacks
public class AgentDetailDialog extends Dialog {
    private final Consumer<AgentDefinition> onSave;  // ✅ just data
    private final Consumer<String> onDelete;          // ✅ just the id
    
    private void handleDelete() {
        onDelete.accept(existingAgent.id());  // ✅ pure UI, no service dependency
    }
}

// AgentListView — view coordinates service and component
grid.addItemClickListener(e -> {
    new AgentDetailDialog(
        e.getItem(),
        updatedDef -> {
            // View owns reactive chain and threading
            agentInfoService.updateAgent(e.getItem().id(), updatedDef)
                .subscribe(
                    info -> UI.getCurrent().access(() -> reloadData()),
                    err -> UI.getCurrent().access(() -> showNotification(err))
                );
        },
        id -> {
            agentInfoService.deleteAgent(id)
                .subscribe(deletedId -> UI.getCurrent().access(() -> reloadData()));
        }
    );
    dialog.open();
});
```

**Why this rule exists**:
- **Threading**: Reactive callbacks run on arbitrary threads; `UI.getCurrent()` is only valid on the Vaadin UI thread. Views control `UI.access()` wrapping; components cannot.
- **Reactive safety**: `Mono<Void>` / `Mono.empty()` completes without emitting a value, so `subscribe(value -> ...)` is **never called**. This caused multiple bugs when reactive chains were embedded in components where the lifecycle and threading were less visible.
- **Testability**: Components with no service dependency are pure UI — testable with browserless tests using just `@ViewPackages` and mock callbacks, no Spring context needed.
- **Re-entrancy**: Views can gate, cancel, or reorder service calls. Components have no visibility into concurrent operations.

> **⚠️ Reactor Gotcha — `Mono<Void>` and `Mono.empty()`**
>
> `Mono.empty()` completes synchronously without emitting a value. The `subscribe(Consumer<T> onNext, Consumer<Throwable> onError)` overload is **never invoked** because there is no `onNext` event to fire.
>
> ```java
> // ❌ BUG — subscribe consumer never called
> Mono<Void> result = someOperation();
> result.subscribe(
>     unused -> System.out.println("This NEVER fires!"),  // skipped!
>     err -> System.out.println("Error handling works")
> );
> ```
>
> To trigger the success consumer, the Mono must emit a value:
> ```java
> // ✅ FIXED — emit a value to trigger the consumer
> Mono<String> result = someOperation();  // returns Mono.just(id)
> result.subscribe(
>     id -> System.out.println("Fires with: " + id),  // called!
>     err -> System.out.println("Error handling works")
> );
> ```
>
> When a service operation doesn't return meaningful data, use `Mono.just(unitValue)` or `Mono.fromRunnable(() -> {...})` if you need to trigger the subscriber for side effects. Alternatively, use `subscribe(onNext, onError, onComplete)` to handle the completion event separately.

#### 1. Lifecycle-Aware Scheduling

Every component that uses a `ScheduledExecutorService` must stop the scheduler in `onDetach()` to prevent memory leaks and stale callbacks after navigation:

```java
@Override
protected void onDetach(DetachEvent detachEvent) {
    stopAutoRefresh();  // Always called in onDetach
    super.onDetach(detachEvent);
}

private void stopAutoRefresh() {
    if (scheduler != null) {
        scheduler.shutdownNow();
        scheduler = null;
    }
}
```

#### 2. UI Thread Safety

All Vaadin component updates must happen on the UI thread via `UI.getCurrent().access()` or `.getUI().get().access()`. Scheduled executors run on background threads:

```java
scheduler.scheduleAtFixedRate(() -> {
    com.vaadin.flow.component.UI.getCurrent().access(() -> {
        // Safe to update components here
        updateDisplay();
    });
}, interval, interval, TimeUnit.SECONDS);
```

#### 3. Reactive Data Loading

For async service calls (e.g., `Flux<AgentInfo>`), use reactive operators with UI thread switching:

```java
agentInfoService.getAllAgentInfos()
    .doFinally(signal -> grid.getUI().get().access(() -> showLoading(false)))
    .subscribe(
        items -> grid.getUI().get().access(() -> updateGrid(items)),
        error -> grid.getUI().get().access(() -> showError(error))
    );
```

#### 4. CSS Theme Organization

All component styles live in the Vaadin theme CSS file (`src/main/frontend/themes/default/styles.css`). Use CSS custom properties from the Lumo theme for consistency:

| Lumo Variable | Usage |
|---------------|-------|
| `var(--lumo-success-color)` | Status UP indicators |
| `var(--lumo-error-color)` | Status DOWN indicators |
| `var(--lumo-warning-color)` | Status WARN indicators |
| `var(--lumo-contrast-10pct)` | Borders, separators |
| `var(--lumo-space-s/m/l)` | Spacing |
| `var(--lumo-border-radius-m)` | Card corners |

#### 5. Status Badge Pattern

Use CSS class switching for status indicators rather than conditional rendering:

```java
private void applyStatusStyles(AdapterStatus adapterStatus) {
    statusBadge.getElement().getClassList().clear();
    switch (adapterStatus) {
        case UP:     statusBadge.addClassName("status-badge-up");     break;
        case DOWN:   statusBadge.addClassName("status-badge-down");   break;
        case WARN:   statusBadge.addClassName("status-badge-warn");   break;
        default:     statusBadge.addClassName("status-badge-unknown");
    }
}
```

### Testing Strategy

Four tiers of testing (fastest → slowest):

| Test Type | Approach | Speed | Example |
|-----------|----------|-------|---------|
| **Unit** | Mockito mocks for services, pure logic | <1 ms | `AgentListViewTest` verifies service injection |
| **Browserless** | `BrowserlessTest` — server-side component queries, no browser | 5–60 ms | `AgentCreationDialogTest` verifies dialog fields, buttons, validation |
| **E2E** | Playwright with real Chromium browser | 1–30 s | `agents.spec.ts`, `observability.spec.ts` |

#### Browserless Testing (Recommended for UI)

Browserless testing (free since Vaadin 25.1) runs entirely on the server side — no browser, no servlet container, no client-server bridge. Tests are typically **100× faster** than E2E and **fail immediately** if a component is misconfigured (unlike Playwright, which waits for timeouts).

Add the dependency (requires Vaadin 25.1+):

```xml
<dependency>
    <groupId>com.vaadin</groupId>
    <artifactId>browserless-test-junit6</artifactId>
    <scope>test</scope>
</dependency>
```

```java
@ViewPackages  // Restricts classpath scanning to test's package only
class AgentCreationDialogTest extends BrowserlessTest {

    private AgentCreationDialog dialog;

    @BeforeEach
    void setup() {
        AgentInfoService mockService = Mockito.mock(AgentInfoService.class);
        dialog = new AgentCreationDialog(mockService);
        UI.getCurrent().add(dialog);
        dialog.open();  // Children aren't queryable until dialog is opened
    }

    @Test
    void dialogContainsExpectedFieldCount() {
        assertThat($(TextField.class).all()).hasSize(3);   // Title, Regex, Template
        assertThat($(TextArea.class).all()).hasSize(2);    // Body, Output Structure
        assertThat($(ComboBox.class).all()).hasSize(1);    // Agent Type
    }

    @Test
    void dialogContainsCancelAndCreateButtons() {
        Button cancelButton = $(Button.class).withText("Cancel").single();
        Button createButton = $(Button.class).withText("Create Agent").single();
        assertThat(createButton.getElement().getThemeList().contains("primary")).isTrue();
    }

    @Test
    void allFormFieldsAreRequired() {
        $(TextField.class).all().forEach(f -> assertThat(f.isRequired()).isTrue());
        $(TextArea.class).all().forEach(a -> assertThat(a.isRequired()).isTrue());
    }
}
```

Key browserless testing patterns:
- **`@ViewPackages`** without arguments restricts scanning to the test's own package — fastest bootstrap
- **`$(ComponentType.class).single()`** queries the component tree — no Page Objects needed
- **Mock services** with Mockito — no Spring context or real services required
- **`dialog.open()`** before queries — overlay components (Dialog, ContextMenu) must be opened for children to be visible
- **Component testers** (`test(component)`) simulate user interactions with built-in usability checks

#### E2E Testing (Playwright)

E2E tests remain for critical flows (navigation, login) and cross-browser verification. They run in `tests/e2e/`, configured via `playwright.config.ts`. Global setup starts the Spring Boot dev server; global teardown stops it. Run with `npm run test:e2e`.

```typescript
// tests/e2e/observability.spec.ts
test('page loads with correct title', async ({ page }) => {
  await page.goto('/observability');
  await expect(page).toHaveTitle(/Observability/i);
});
```

**Avoid** `page.waitForTimeout()` in E2E tests. Use Playwright's implicit waits instead:

```typescript
// ❌ Bad — fixed sleep, never fails fast
await page.waitForTimeout(5000);

// ✅ Good — waits only as long as needed, fails immediately if element never appears
await expect(page.locator('[data-testid="status-card"]')).toBeVisible();
```

**Use `data-testid` attributes** for stable locators:

```java
// Java side
Button submitButton = new Button("Submit");
submitButton.setTestId("submit-button");
```

```typescript
// Playwright side
await page.locator('[data-testid="submit-button"]').click();
```

## See Also

- [ADR-001: REST Adapters](adr-rest-adapters.md) — REST API layer that the UI does not depend on (services are injected directly)
- [ADR-003: UI Views and Routing](adr-ui-views.md) — View routing, Hilla/Flow coexistence, navigation layout
- [ADR: Application Observability with LLM Health Monitoring](adr-application-observability.md) — Backend observability service that the ObservabilityView displays
- [ADR: Hilla Setup Guide](adr-hilla-setup-guide.md) — Hilla configuration (available but not actively used for current UI)

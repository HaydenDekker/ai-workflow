# ADR-003: UI Views and Routing

## Date

2026-04-21

## Context

The application uses **Vaadin 25 with Hilla** — a hybrid framework where both Java-based Flow views and TypeScript-based Hilla views coexist in the same application. The existing UI has:

- **Java Flow views** (`AgentListView` at `@Route("agents")`, `ObservabilityView` at `@Route("observability")`) — server-side rendered Vaadin components with reactive data loading
- **Hilla layout** (`@layout.tsx`) — a React component that renders the shared `AppLayout` with a top navigation bar and an `<Outlet>` for child routes
- **Hilla home view** (`@index.tsx`) — a React component with a simple "Say hello" interaction

The Hilla file router (`@vaadin/hilla-file-router`) automatically discovers route modules by scanning `src/main/frontend/views/*.tsx` files. It reads `export const config: ViewConfig` to determine menu order, icons, and titles. These discovered routes populate the top navigation bar via `createMenuItems()`.

**Problem**: The Java Flow views are not visible in the top navigation bar. The Hilla file router only discovers `.tsx` route files — it has no mechanism to discover Java `@Route` annotations. Additionally, attempting to create matching Hilla wrapper files (`agents.tsx`, `observability.tsx`) that import the Flow views caused a **route conflict error** — both the Hilla route and the Flow route claimed the same URL path, and Vaadin rejected the application at startup with `InvalidRouteConfigurationException`.

## Decision

We will use a **hybrid navigation approach**:

1. **Hilla-managed routes** (`.tsx` files in `views/`) are auto-discovered by the file router and appear in `createMenuItems()`. They render React components or use the `<Flow />` component for Flow integration.
2. **Flow-managed routes** (`@Route`-annotated Java classes) are **not** auto-discovered. Navigation links for them must be **manually added** to the Hilla `@layout.tsx` component.
3. **No path sharing** — a URL path cannot be claimed by both a Hilla route and a Flow route. The file router and the Flow router register separate route trees, and overlapping paths cause a startup failure.

### Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    Spring Boot Application (Fat JAR)                    │
│                                                                         │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │                    Hilla / React Layer (Client)                 │   │
│  │                                                                 │   │
│  │  ┌─────────────────────────────────────────────────────────┐   │   │
│  │  │  @layout.tsx — MainLayout                              │   │   │
│  │  │                                                         │   │   │
│  │  │  <AppLayout>                                            │   │   │
│  │  │    <header slot="navbar">                               │   │   │
│  │  │      {createMenuItems().map(...)}    ← Auto-discovered  │   │   │
│  │  │      {flowRoutes.map(...)}             ← Manually added │   │   │
│  │  │    </header>                                            │   │   │
│  │  │    <Outlet />                         ← Child routes    │   │   │
│  │  │  </AppLayout>                                           │   │   │
│  │  └─────────────────────────────────────────────────────────┘   │   │
│  │                                                                 │   │
│  │  src/main/frontend/views/                                       │   │
│  │  ├── @layout.tsx         ← Hilla layout (navigation + outlet)   │   │
│  │  └── @index.tsx          ← Home view (Hilla)                   │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│                                                                          │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │                    Flow Layer (Server)                          │   │
│  │                                                                 │   │
│  │  @Route("agents")           AgentListView.java                 │   │
│  │  @Route("observability")    ObservabilityView.java             │   │
│  │                                                                 │   │
│  │  These render via Flow's server-side component tree,           │   │
│  │  injected into the Hilla <Outlet> via the Flow router.         │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

### Component Structure

```
src/main/java/com/hdekker/ai_workflow/ui/views/
├── AgentListView.java              # Flow view — @Route("agents")
├── ObservabilityView.java          # Flow view — @Route("observability")
└── MainLayout.java                 # [REMOVED] — Not used; Hilla handles layout

src/main/frontend/views/
├── @layout.tsx                     # Hilla layout — navigation + Outlet
└── @index.tsx                      # Home view — Hilla React component

src/main/frontend/generated/
└── file-routes.ts                  # Auto-generated by Hilla file router
```

### Code Examples

**Hilla Layout — Navigation with Flow Route Links**

```tsx
// src/main/frontend/views/@layout.tsx
import { createMenuItems, useViewConfig } from "@vaadin/hilla-file-router/runtime.js";
import { effect, signal } from "@vaadin/hilla-react-signals";
import { AppLayout, Icon } from "@vaadin/react-components";
import { Suspense, useEffect } from "react";
import { NavLink, Outlet } from "react-router";

// Flow routes (server-side Vaadin views) — not auto-discovered by Hilla
const flowRoutes = [
  { to: "agents", title: "Agent List", icon: "line-awesome/svg/list-solid.svg" },
  { to: "observability", title: "Observability", icon: "line-awesome/svg/chart-bar-solid.svg" },
];

export default function MainLayout() {
  return (
    <AppLayout>
      <header slot="navbar">
        <nav>
          <ul>
            {/* Auto-discovered Hilla routes */}
            {createMenuItems().map(({ to, title, icon }) => (
              <li key={to}>
                <NavLink to={to}>
                  {icon ? <Icon src={icon} /> : <></>}
                  <span>{title}</span>
                </NavLink>
              </li>
            ))}
            {/* Manually added Flow routes */}
            {flowRoutes.map(({ to, title, icon }) => (
              <li key={"flow-" + to}>
                <NavLink to={to}>
                  {icon ? <Icon src={icon} /> : <></>}
                  <span>{title}</span>
                </NavLink>
              </li>
            ))}
          </ul>
        </nav>
      </header>
      <Suspense><Outlet /></Suspense>
    </AppLayout>
  );
}
```

**Java Flow View — AgentListView**

```java
@Route("agents")
@PageTitle("Agent List")
public class AgentListView extends VerticalLayout
        implements AfterNavigationObserver {

    private final Grid<AgentInfo> grid;
    private final AgentInfoService agentInfoService;

    @Autowired
    public AgentListView(AgentInfoService agentInfoService) {
        this.agentInfoService = agentInfoService;
        initLayout();
    }

    @Override
    public void afterNavigation(AfterNavigationEvent event) {
        reloadData();
    }
}
```

**Java Flow View — ObservabilityView**

```java
@Route("observability")
@PageTitle("Observability")
public class ObservabilityView extends VerticalLayout
        implements AfterNavigationObserver {

    private final LLMStatusService llmStatusService;
    private ScheduledExecutorService viewRefreshScheduler;

    @Override
    public void afterNavigation(AfterNavigationEvent event) {
        startViewAutoRefresh();
        loadStatusCards();
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        stopViewAutoRefresh();
        super.onDetach(detachEvent);
    }
}
```

### Configuration

**No additional configuration required.** The Hilla file router auto-generates `file-routes.ts` from `views/*.tsx` files. Flow routes are discovered by the Vaadin servlet via `@Route` annotations.

### Styling

Navigation links in `@layout.tsx` use Lumo utility classes for consistent styling:

```tsx
className="flex gap-xs h-m items-center px-s text-body"
```

CSS custom properties for component styles remain in `src/main/frontend/themes/default/styles.css` (see ADR-002).

### Testing Strategy

| Test Type | Approach | Example |
|-----------|----------|---------|
| **Unit** | Verify `@Route` annotation presence | `AgentListViewTest` checks class can be loaded |
| **E2E** | Playwright navigates via URL and verifies content | `navigation.spec.ts` checks links render and navigation works |

E2E tests validate:
- Navigation bar links are present and clickable
- Clicking a link navigates to the correct route
- The destination view renders its expected content (headings, data)
- Page titles update on navigation

```typescript
// tests/e2e/navigation.spec.ts
test('Agents navigation link is visible and clickable', async ({ page }) => {
  await page.goto('/');
  await page.waitForTimeout(3000);

  const menuButton = page.locator('vaadin-menu-bar-button').first();
  await menuButton.click();
  await page.waitForTimeout(1000);

  const agentsItem = page.locator('vaadin-item', { hasText: 'Agents' }).first();
  await expect(agentsItem).toBeVisible();
  await agentsItem.click();
  await page.waitForTimeout(3000);

  await expect(page).toHaveURL(/\/agents/i);
});
```

## Consequences

### Benefits

- **No route conflicts** — Flow routes and Hilla routes use separate URL namespaces; no path overlap means no `InvalidRouteConfigurationException`.
- **Simple manual navigation** — Adding a new Flow view requires only a `@Route` annotation in Java and one line in the `flowRoutes` array in `@layout.tsx`.
- **Hilla file router stays clean** — Auto-discovery works for all `.tsx` routes without needing Flow-aware scanning logic.
- **Single navigation source of truth** — The `@layout.tsx` header renders all navigation links (both Hilla and Flow), so there's one place to add new links.

### Trade-offs

- **Manual link maintenance** — Every new Flow view requires an update in two places: the Java `@Route` annotation and the `flowRoutes` array. If they diverge, navigation links point to dead routes.
- **No auto-icon/title discovery** — The file router extracts `title` and `icon` from `ViewConfig` in `.tsx` files. Flow views have no equivalent metadata, so these must be manually specified in `@layout.tsx`.
- **Navigation bar is Hilla-only** — Flow views cannot inject their own navigation items. The nav bar is a React component and runs on the client side.

### Tight Coupling Points

- **`@layout.tsx` `flowRoutes` array** — This is the single point of failure for Flow route navigation. If a new Flow view is added without updating this array, it won't appear in the nav bar. If the array entry has the wrong path, navigation breaks.
- **Path naming consistency** — The `to` property in `flowRoutes` must exactly match the `@Route` value in the Java class. There's no compile-time check to enforce this.

### Important Notes

- **`@PWA` restriction** — The `@PWA` annotation can only be placed on a class implementing `AppShellConfigurator` (in this project, `AiWorkflowApplication`). Placing it on an `AppLayout` subclass causes a startup failure.
- **Hilla file router generates `file-routes.ts`** — This file is regenerated on every build. Manual edits to it will be lost. Route definitions must live in `views/*.tsx` files.
- **Flow routes render via `Flow` component** — When a Flow route is navigated to, Vaadin's `Flow.tsx` component (in `generated/flow/Flow.tsx`) handles the server-side rendering and injects the Flow container into the React `<Outlet>`.

## How-To Guides

### How to Add a New Flow View

1. Create the Java view class with `@Route("path")` and `@PageTitle("Title")` annotations.
2. Open `src/main/frontend/views/@layout.tsx`.
3. Add an entry to the `flowRoutes` array:

   ```tsx
   const flowRoutes = [
     // ... existing entries
     { to: "new-view", title: "New View", icon: "line-awesome/svg/some-icon.svg" },
   ];
   ```

4. No changes to `file-routes.ts` are needed — it is auto-generated.

### How to Add a New Hilla View

1. Create `src/main/frontend/views/new-view.tsx`.
2. Export a `ViewConfig`:

   ```tsx
   export const config: ViewConfig = {
     menu: { order: 3, icon: "line-awesome/svg/icon.svg" },
     title: "New View",
   };
   ```

3. Export the default component.
4. The file router will auto-discover it — no manual nav bar update needed.

### How to Swap a Flow View Route Path

1. Change the `@Route("new-path")` annotation in the Java class.
2. Update the `to` property in the corresponding `flowRoutes` entry in `@layout.tsx`.
3. Both must match — there is no automated validation.

## Alternatives Considered

### Alternative 1: Hilla Wrapper Files for Flow Views

**Description**: Create `agents.tsx` and `observability.tsx` files that render `<Flow />`, allowing the file router to auto-discover Flow routes.

**Why considered**: Would unify the navigation — all routes would be discovered automatically via `createMenuItems()`, eliminating the manual `flowRoutes` array.

**Why rejected**: The file router generates routes with `flowLayout: false` for these files. When both a Hilla route (e.g., `agents.tsx`) and a Flow route (`@Route("agents")`) claim the same path, Vaadin throws `InvalidRouteConfigurationException`. The file router and Flow router both register route handlers for the same URL, and Vaadin's route configuration validator rejects this overlap.

### Alternative 2: Custom File Router Plugin

**Description**: Write a custom Hilla file router plugin that scans Java `@Route` annotations and generates corresponding entries in `file-routes.ts`.

**Why considered**: Would fully automate Flow route discovery, eliminating manual navigation link maintenance.

**Why rejected**: Adds build-time complexity and couples the frontend build pipeline to Java classpath scanning. The manual approach is simpler and sufficiently low-maintenance for the current number of views (2 Flow, 1 Hilla). The overhead of maintaining a custom plugin outweighs the benefit for a small application.

### Alternative 3: Pure Flow Navigation

**Description**: Use `@Route` with `layout = MainLayout.class` (Java `AppLayout` subclass) for all views, abandoning the Hilla layout entirely.

**Why considered**: Would eliminate the Hilla/Flow integration complexity entirely.

**Why rejected**: The project has already invested in the Hilla file router and React-based `@layout.tsx`. Switching to a pure Java layout would require rewriting the existing home view (`@index.tsx`) and would lose Hilla features like code-splitting and the file router's automatic route generation.

## Migration Path

This ADR represents the current state of the application. No migration is needed — the hybrid navigation approach was adopted during initial UI development.

## Open Questions

- **Q:** Should Flow view metadata (title, icon) be defined in a shared configuration so it can be read by both the Java annotation and the Hilla layout?
  **Decision:** Pending — for now, duplication in `@Route` and `flowRoutes` is acceptable.

- **Q:** Will the number of Flow views grow, making manual maintenance painful?
  **Decision:** Monitor — if Flow views exceed ~5, revisit Alternative 2 (custom file router plugin).

## See Also

- [ADR-001: REST Adapters](adr-rest-adapters.md) — REST API layer
- [ADR-002: Vaadin/Hilla UI Components](adr-ui-components.md) — Component building principles, lifecycle management, CSS theming
- [ADR: Application Observability with LLM Health Monitoring](adr-application-observability.md) — Backend service displayed by the ObservabilityView
- [ADR: Hilla Setup Guide](adr-hilla-setup-guide.md) — Hilla configuration and file router basics

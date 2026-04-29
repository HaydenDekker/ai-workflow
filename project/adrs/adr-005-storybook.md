# ADR-005: Storybook

## Date

2026-04-23

## Context

The application uses **Vaadin 25 with Hilla** for its UI layer ([ADR-003](adr-003-vaadin-hilla-ui.md), [ADR-004](adr-004-flow-hilla-routing.md)). Views are written as Java classes with `@Route`, and reusable components are Java-side Vaadin components. However, the application also has a **Hilla frontend layer** (`src/main/frontend/`) where React views and components live (e.g., the `HomeView.tsx` index view).

As the frontend grows, we need a way to:

1. **Develop and inspect React components in isolation** — without running the full Spring Boot server, navigating to a route, or triggering backend calls.
2. **Visualize component variants** — toggle props like `greeting` or `showInput` and see the result instantly.
3. **Document component APIs** — auto-generated docs from JSDoc and props, discoverable in a central UI.
4. **Test component interactions** — verify user events (click, type) produce the expected DOM output without a real backend.
5. **Catch regressions visually** — a component that looks correct in code may render unexpectedly in the browser.

Existing options:
- **Playwright E2E** — Tests the full app against a running server. Slow (seconds per test), fragile (depends on server startup, network, browser), not suitable for isolated component inspection.
- **Vitest + React Testing Library** — Tests individual components in a browser context. Good for assertions, but provides no visual playground or live prop controls.
- **Storybook** — Purpose-built for component isolation, visual inspection, interactive prop controls, and documentation. Runs independently of the Spring Boot server.

Requirements:
1. **Component isolation** — Render a single component with controlled props, no routing, no backend dependency.
2. **Interactive controls** — Change props live and see the result immediately.
3. **Auto-generated docs** — Props table, description, and usage examples derived from the component code.
4. **Static build** — Deployable HTML output for offline review or CI artifact inspection.
5. **Zero backend required** — Components that call `HelloWorldService.sayHello()` should mock or skip the call.

## Decision

We will use **Storybook 10.3.5** (React + Vite framework) as the component development and visual inspection tool for all Hilla/React UI components.

### Architecture Overview

```
┌──────────────────────────────────────────────────────────────────────┐
│                     Component Development Workflow                    │
│                                                                      │
│  ┌────────────────────────────────────────────────────────────────┐  │
│  │  Storybook (port 6006)                                         │  │
│  │                                                                 │  │
│  │  ┌─────────────────┐  ┌──────────────┐  ┌──────────────────┐  │  │
│  │  │  Component      │  │  Story file  │  │  Addon-docs      │  │  │
│  │  │  (HelloWorld)   │  │  (*.stories  │  │  (Auto-generated │  │  │
│  │  │  .tsx)          │  │   .tsx)      │  │   props table,   │  │  │
│  │  │                 │  │              │  │   docs panel)    │  │  │
│  │  │  Props:         │  │  Default     │  │                  │  │  │
│  │  │  greeting       │  │  Custom      │  │  ┌────────────┐  │  │  │
│  │  │  showInput      │  │  NoInput     │  │  │ Controls   │  │  │  │
│  │  └────────┬────────┘  └──────────────┘  │  │ panel      │  │  │  │
│  │           │                              │  └────────────┘  │  │  │
│  │           │  Vite + React JSX             │                  │  │  │
│  │           ▼  (uses project tsconfig)      │  ┌────────────┐  │  │  │
│  │  ┌─────────────────┐                      │  │ Docs panel │  │  │  │
│  │  │  Browser iframe │                      │  │ (auto-gen) │  │  │  │
│  │  │  (Chromium)     │                      │  └────────────┘  │  │  │
│  │  └─────────────────┘  Built-in addons: │                  │  │  │  │
│  │                         viewport,       │  ┌────────────┐  │  │  │
│  │                         controls,       │  │ Preview    │  │  │  │
│  │                         actions,        │  │ (live     │  │  │  │
│  │                         test (expect,   │  │  render)  │  │  │  │
│  │                         userEvent)      │  └────────────┘  │  │  │
│  └────────────────────────────────────────────────────────────────┘  │
│                                                                      │
│  ┌────────────────────────────────────────────────────────────────┐  │
│  │  Storybook build output (storybook-static/)                    │  │
│  │                                                                 │  │
│  │  index.html  ──►  Deployable static site for offline review    │  │
│  └────────────────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────────────┘
```

### Component Structure

```
src/main/frontend/
├── components/
│   ├── HelloWorld.tsx              # Reusable component
│   ├── HelloWorld.stories.tsx      # Storybook stories for the component
│   └── ...                         # Future components follow same pattern
├── views/
│   ├── @index.tsx                  # Hilla route: /
│   └── @layout.tsx                 # Hilla route layout
└── themes/
    └── default/
        └── styles.css              # Vaadin theme (shared with Storybook)

.storybook/
├── main.ts                         # Storybook config (framework, stories glob)
└── preview.ts                      # Preview defaults (controls, docs)
```

### Storybook Configuration

**`.storybook/main.ts`**

```typescript
import type { StorybookConfig } from '@storybook/react-vite';

const config: StorybookConfig = {
  stories: [
    "../src/main/frontend/**/*.mdx",
    "../src/main/frontend/**/*.stories.@(js|jsx|mjs|ts|tsx)"
  ],
  addons: [
    '@storybook/addon-docs'
  ],
  framework: {
    name: '@storybook/react-vite',
    options: {}
  },
  staticDirs: ['../src/main/frontend/themes']
};
export default config;
```

Key decisions:
- **Framework: `@storybook/react-vite`** — Uses Vite (already configured by Vaadin for Hilla), so the same `tsconfig.json`, path aliases (`Frontend/*`), and JSX transform apply.
- **Stories glob: `src/main/frontend/**/*.stories.tsx`** — Co-located with components.
- **Addon: `@addon-docs` only** — In Storybook v10, viewport, controls, interactions, and actions are **built into core** (`@storybook/addon-essentials` was removed). Only docs remains a separate package.
- **`staticDirs`** — Includes the Vaadin theme folder so CSS custom properties and component styles are available in Storybook previews.

**`.storybook/preview.ts`**

```typescript
import type { Preview } from '@storybook/react';

const preview: Preview = {
  parameters: {
    controls: {
      matchers: {
        color: /(background|color)$/i,
        date: /Date$/i,
      },
    },
    docs: {
      toc: true,
    },
  },
};

export default preview;
```

### Code Examples

**Component — `HelloWorld.tsx`**

```tsx
import { useSignal } from "@vaadin/hilla-react-signals";
import { Button, TextField } from "@vaadin/react-components";

export interface HelloWorldProps {
  /** The greeting message to display */
  greeting?: string;
  /** Whether to show the name input field */
  showInput?: boolean;
}

export function HelloWorld({
  greeting = "Hello",
  showInput = true,
}: HelloWorldProps) {
  const name = useSignal("");

  return (
    <div className="flex flex-col gap-m p-m" style={{ maxWidth: 400 }}>
      <h2>{greeting}</h2>
      {showInput && (
        <div className="flex gap-s items-end">
          <TextField
            label="Your name"
            value={name.value}
            onValueChanged={(e) => { name.value = e.detail.value; }}
          />
          <Button onClick={() => alert(`${greeting}, ${name.value || "World"}!`)}>
            Say Hello
          </Button>
        </div>
      )}
      {!showInput && <p>{greeting}, World!</p>}
    </div>
  );
}
```

**Stories — `HelloWorld.stories.tsx`**

```tsx
import type { Meta, StoryObj } from "@storybook/react";
import { expect, userEvent, within } from "storybook/test";
import { HelloWorld } from "./HelloWorld";

const meta = {
  title: "Components/HelloWorld",
  component: HelloWorld,
  tags: ["autodocs"],
  argTypes: {
    greeting: {
      control: "text",
      description: "The greeting message to display",
    },
    showInput: {
      control: "boolean",
      description: "Whether to show the name input field",
    },
  },
  parameters: {
    docs: {
      description: {
        component: "A simple greeting component with optional name input.",
      },
    },
  },
} satisfies Meta<typeof HelloWorld>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = { args: { greeting: "Hello", showInput: true } };
export const CustomGreeting: Story = { args: { greeting: "Welcome", showInput: true } };
export const NoInput: Story = { args: { greeting: "Hello", showInput: false } };
```

### Configuration

**`package.json` scripts**

```json
{
  "scripts": {
    "storybook": "storybook dev -p 6006",
    "build-storybook": "storybook build"
  }
}
```

| Command | Purpose |
|---------|---------|
| `npm run storybook` | Starts dev server at `http://localhost:6006`. Hot-reloads on file changes. |
| `npm run build-storybook` | Builds static HTML to `storybook-static/`. Deployable or reviewable offline. |

### Dependencies

```json
{
  "devDependencies": {
    "storybook": "^10.3.5",
    "@storybook/react-vite": "^10.3.5",
    "@storybook/addon-docs": "^10.3.5"
  }
}
```

In Storybook v10, **no other addon packages are needed** — viewport, controls, interactions, actions, and testing (`expect`, `spy`, `userEvent`) are built into the core `storybook` package.

### Component Development Workflow

The complete lifecycle for creating a new Hilla/React component, from scratch to integration:

#### Step 1 — Create the Component File

**Location:** `src/main/frontend/components/<ComponentName>.tsx`

Define a typed `Props` interface with JSDoc comments, then implement the component:

```tsx
// src/main/frontend/components/StatusBadge.tsx
export interface StatusBadgeProps {
  /** Display label for the status */
  label?: string;
  /** Current status value */
  status: "up" | "down" | "warn" | "unknown";
}

export function StatusBadge({ label = "Status", status }: StatusBadgeProps) {
  const color =
    status === "up" ? "green" : status === "down" ? "red" : status === "warn" ? "orange" : "gray";
  return (
    <span style={{ color, fontWeight: "bold" }}>
      {label} ({status})
    </span>
  );
}
```

**Key rules:**
- Export a typed `Props` interface (naming: `<ComponentName>Props`).
- Use JSDoc comments on each prop — Storybook's docs panel generates the props table from these.
- Set sensible defaults for optional props.
- If the component calls Hilla endpoints, use either `vi.mock()` (see mocking patterns) or graceful degradation (see below).

#### Step 2 — Create the Story File (Co-located)

**Location:** `src/main/frontend/components/<ComponentName>.stories.tsx`

```tsx
// src/main/frontend/components/StatusBadge.stories.tsx
import type { Meta, StoryObj } from "@storybook/react";
import { StatusBadge } from "./StatusBadge";

const meta = {
  title: "Components/StatusBadge",
  component: StatusBadge,
  tags: ["autodocs"],
  argTypes: {
    status: { control: "select", options: ["up", "down", "warn", "unknown"] },
    label: { control: "text" },
  },
  parameters: {
    docs: {
      description: {
        component: "A simple status badge with color-coded status indicator.",
      },
    },
  },
} satisfies Meta<typeof StatusBadge>;

export default meta;
type Story = StoryObj<typeof meta>;

// Default — most common usage
export const Default: Story = { args: { status: "up", label: "Service A" } };

// Variants for each status
export const Down: Story = { args: { status: "down", label: "Service B" } };
export const Warn: Story = { args: { status: "warn", label: "Service C" } };
export const Unknown: Story = { args: { status: "unknown", label: "Service D" } };

// Edge cases
export const NoLabel: Story = { args: { status: "up" } };
export const WithInteraction: Story = {
  args: { status: "up", label: "Clickable" },
  play: async ({ canvasElement }) => {
    const { within, expect, userEvent } = await import("storybook/test");
    const canvas = within(canvasElement);
    const badge = canvas.getByText("Clickable (up)");
    expect(badge).toBeInTheDocument();
    await userEvent.hover(badge);
    // Add hover assertions here
  },
};
```

**Story file rules:**
- Co-locate with the component (`Component.stories.tsx` next to `Component.tsx`).
- Use `tags: ["autodocs"]` to enable auto-generated docs panel.
- Provide a `Default` story representing the most common usage.
- Cover all prop variants and edge cases as separate named stories.
- Use `play` functions for interaction testing (click, hover, type).

#### Step 3 — Run Storybook and Inspect

```bash
npm run storybook
# Opens http://localhost:6006
```

In the Storybook UI:
- **Sidebar** — Navigate the story tree (e.g., `Components/StatusBadge`).
- **Controls panel** — Live-edit props to see changes instantly.
- **Docs panel** — Auto-generated props table, description, and source code.
- **Interaction panel** — If a `play` function is defined, step through interactions.

#### Step 4 — Add Interaction Tests (Optional but Recommended)

```tsx
export const WithInteraction: Story = {
  args: { status: "up", label: "Clickable" },
  play: async ({ canvasElement }) => {
    const { within, expect, userEvent } = await import("storybook/test");
    const canvas = within(canvasElement);
    const badge = canvas.getByText("Clickable (up)");
    expect(badge).toBeInTheDocument();
    await userEvent.click(badge);
    expect(badge).toHaveAttribute("aria-pressed", "true");
  },
};
```

#### Step 5 — Build Static Output (For Review/CI)

```bash
npm run build-storybook
# Outputs to storybook-static/
# Can be served with any static file server or committed to a branch for review
```

#### Step 6 — Integrate into the Application

Once the component is designed and tested in Storybook, import it into the Hilla views or layout:

```tsx
// src/main/frontend/views/@layout.tsx
import { StatusBadge } from "Frontend/components/StatusBadge";

// Usage in a view:
<StatusBadge status={agentStatus} label={agentName} />
```

For Flow (Java) views, use the Vaadin component equivalents (see [ADR-003](adr-003-vaadin-hilla-ui.md)).

#### Step 7 — Write Browserless Tests (If Applicable)

For components that also exist in the Flow layer (Java), write browserless tests in `src/test/java/**/components/`:

```java
@ViewPackages
class StatusBadgeTest extends BrowserlessTest {
    @Test
    void rendersUpStatusInGreen() {
        var badge = new AdapterStatusComponent(LLMStatus.UP);
        UI.getCurrent().add(badge);
        var icon = $(Icon.class).single();
        assertThat(icon.getColor()).isEqualTo("success");
    }
}
```

> **See:** [ADR-003](adr-003-vaadin-hilla-ui.md) for browserless testing patterns.

### Quick-Reference: Component vs. Story Checklist

| Item | Component (`.tsx`) | Story (`.stories.tsx`) |
|------|-------------------|----------------------|
| Props interface with JSDoc | ✅ Required | — |
| Default prop values | ✅ Recommended | — |
| `Meta` object with `component` reference | — | ✅ Required |
| `tags: ["autodocs"]` | — | ✅ Required |
| `Default` story | — | ✅ Required |
| Variant stories for all prop combinations | — | ✅ Recommended |
| `play` function for interaction testing | — | ✅ Optional but recommended |
| `vi.mock()` for Hilla endpoints | — | ✅ Required if component calls endpoints |
| Graceful degradation (try/catch fallback) | ✅ If no `vi.mock()` | — |
| JSDoc explaining fallback behavior | ✅ If using graceful degradation | — |

### Testing Strategy

Storybook v10 includes built-in testing via `storybook/test`:

```tsx
import { expect, userEvent, within } from "storybook/test";

export const Default: Story = {
  args: { greeting: "Hello", showInput: true },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    const heading = canvas.getByText("Hello");
    expect(heading).toBeInTheDocument();

    const button = canvas.getByText("Say Hello");
    await userEvent.click(button);
    // Further assertions...
  },
};
```

The testing module provides:
- `expect` — chai-style assertions with `@testing-library/jest-dom` matchers
- `userEvent` — Simulates real user interactions (click, type, hover)
- `within` — Scopes queries to the Storybook canvas iframe
- `spy` — Mocks and spies for module-level functions

| Test Type | Where | Tool |
|-----------|-------|------|
| **Component visual** | Storybook dev server | Interactive preview + Controls |
| **Component interaction** | `play` function in stories | `storybook/test` (`expect`, `userEvent`) |
| **Component unit** | `frontend/tests/*.test.tsx` | Vitest + React Testing Library |
| **Flow component** | `src/test/java/**/components` | `BrowserlessTest` (`BrowserlessTest` + Mockito) |
| **View E2E** | `tests/e2e/*.spec.ts` | Playwright + Spring Boot server |

> **Note:** Browserless testing (`BrowserlessTest`) is the preferred approach for testing Java-side Flow components and views. It runs 100× faster than Playwright and provides direct Java API access. See [ADR-003](adr-003-vaadin-hilla-ui.md) for details.

### Service Mocking Workaround — Graceful Degradation

When a Hilla component calls generated endpoints (`Frontend/generated/endpoints`), two approaches exist for Storybook isolation:

1. **`vi.mock()` (preferred for complex components)** — Mock the endpoint module with local data. See the "No Backend Calls in Stories" section above for full patterns.
2. **Graceful degradation (fallback pattern)** — Wrap endpoint calls in `try/catch` and fall back to local state updates. This allows the component to render and be interactive in Storybook **without any mocking setup**, at the cost of not persisting state to the server.

The graceful degradation pattern is simple but effective for demo widgets, simple stateful components, and components where the backend interaction is secondary to the UI behavior being tested:

```tsx
// src/main/frontend/components/Counter.tsx
export function Counter({ label = "Counter", initialCount = 0 }: CounterProps) {
  const count = useSignal(initialCount);

  const handleIncrement = async () => {
    try {
      const result = await CounterService.increment(count.value).block();
      count.value = result.currentCount;
    } catch (error) {
      console.error("Failed to increment counter:", error);
      count.value = count.value + 1; // fallback for offline/demo
    }
  };

  const handleDecrement = async () => {
    try {
      const result = await CounterService.decrement(count.value).block();
      count.value = result.currentCount;
    } catch (error) {
      console.error("Failed to decrement counter:", error);
      count.value = count.value - 1; // fallback for offline/demo
    }
  };

  // ... similar for reset, etc.
}
```

**When to use each approach:**

| Scenario | Preferred Approach | Reason |
|----------|-------------------|--------|
| Complex component with multiple endpoint calls | `vi.mock()` | Single mock covers all calls; easy to maintain |
| Simple stateful component (counter, toggle, etc.) | Graceful degradation | Zero setup; component works out of the box |
| Component used in both Storybook and production | `vi.mock()` | Explicit mock makes the separation clear |
| Demo/prototype widget | Graceful degradation | Fast iteration; no mock maintenance |
| Component with conditional endpoint calls | `vi.mock()` | Easier to test each code path |

**Caveats of graceful degradation:**
- The fallback path is not tested by Storybook interactions (only the happy path is exercised in `vi.mock()` stories).
- The fallback behavior may differ from the server response (e.g., the server might validate the input and reject invalid operations).
- Add a JSDoc comment to the component explaining the fallback so future maintainers understand the pattern.

> **Note:** The `Counter` component (placed in the layout drawer as a demo widget) demonstrates this pattern. It works in Storybook without `vi.mock()` because the try/catch silently falls back to local state. In production with a running server, the `try` branch succeeds and persists state to the backend.

### Real-World Example: Counter Component

The `Counter` component demonstrates the complete workflow including the graceful degradation workaround:

**1. Component** (`src/main/frontend/components/Counter.tsx`):
- Defines `CounterProps` with `label` and `initialCount` props
- Calls `CounterService.increment/decrement/reset` (generated Hilla endpoints)
- Uses **graceful degradation** — `try/catch` falls back to local state if the server is unavailable
- JSDoc comment explains the fallback pattern

**2. Story** (`src/main/frontend/components/Counter.stories.tsx`):
- Uses `vi.mock()` is **not** needed because the component has graceful degradation
- `Default` story with `initialCount: 0`
- `play` function tests click interactions (increment, decrement, reset)
- `StartingAtFive` and `NegativeStart` cover edge cases

**3. Integration** (`src/main/frontend/views/@layout.tsx`):
- Imported and placed in the drawer slot as a demo widget
- Works in production (server running) and Storybook (no server) seamlessly

This pattern is ideal for simple stateful widgets. For components with complex endpoint interactions, prefer `vi.mock()` for explicit, testable mocks.

### Component Conventions

#### 1. Co-location

Stories live next to components (`Component.tsx` + `Component.stories.tsx`). Storybook auto-discovers them via the glob pattern in `main.ts`.

#### 2. Props Interface

Every component exports a typed `Props` interface with JSDoc comments. Storybook's docs panel generates the props table from these types.

#### 3. Default Story Args

The `Default` story should represent the most common usage. Other stories cover edge cases and variants.

#### 4. Auto-docs Tag

Include `tags: ["autodocs"]` in the meta to enable the docs panel with props table, description, and source code.

#### 5. No Backend Calls in Stories — Direct Service Mocking with `vi.mock()`

Components that call Hilla endpoints (generated in `Frontend/generated/endpoints.ts`) **must not** require a running backend server to render in Storybook. The primary approach is **direct module mocking with `vi.mock()`** using local mock data.

This is the default and preferred strategy. It requires zero infrastructure — no Spring Boot server, no MSW setup, no proxy config. Just a mock file and a `vi.mock()` call in the story.

##### Pattern A: Mock the Generated Endpoint Module

When a component imports from `Frontend/generated/endpoints.ts` (or a specific endpoint file), mock the entire module:

```tsx
// src/main/frontend/views/agent-list/AgentList.stories.tsx
import type { Meta, StoryObj } from "@storybook/react";
import { AgentList } from "./AgentList";

// Mock the generated endpoint module with local data
vi.mock("Frontend/generated/endpoints", async (importOriginal) => {
  const actual = await importOriginal<typeof import("Frontend/generated/endpoints")>();
  return {
    ...actual,
    AgentInfoService: {
      getAllAgentInfos: vi.fn(() => ({
        block: () =>
          Promise.resolve([
            {
              id: "mock-agent-1",
              definition: {
                title: "File Processor",
                agentType: "Map",
                fileInputRegex: "\\.txt$",
                targetDirectory: "/tmp/output",
              },
              source: "local",
              scannerId: "scan-001",
              createdAt: "2025-01-15T10:00:00Z",
              active: true,
            },
            {
              id: "mock-agent-2",
              definition: {
                title: "Data Aggregator",
                agentType: "Reducer",
                fileInputRegex: "\\.csv$",
                targetDirectory: "/tmp/aggregated",
              },
              source: "local",
              scannerId: "scan-002",
              createdAt: "2025-02-20T14:30:00Z",
              active: false,
            },
          ]),
      })),
      refreshAgent: vi.fn(() => ({
        block: () =>
          Promise.resolve({
            id: "mock-agent-1",
            definition: { title: "File Processor" },
            createdAt: "2025-01-15T10:00:00Z",
            active: true,
          }),
      })),
    },
  };
});

const meta = {
  title: "Views/AgentList",
  component: AgentList,
  tags: ["autodocs"],
} satisfies Meta<typeof AgentList>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {};
export const Empty: Story = {
  parameters: {
    mockReset: true,
  },
  play: async () => {
    // Override mock for this specific story
    vi.mocked(AgentInfoService.getAllAgentInfos).mockReturnValue({
      block: () => Promise.resolve([]),
    });
  },
};
export const WithError: Story = {
  play: async () => {
    vi.mocked(AgentInfoService.getAllAgentInfos).mockRejectedValue(
      new Error("Service unavailable")
    );
  },
};
```

##### Pattern B: Mock a Specific Endpoint File

When endpoints are split into separate generated files (e.g., `Frontend/generated/endpoints/AgentInfoService.ts`):

```tsx
// AgentList.stories.tsx
vi.mock("Frontend/generated/endpoints/AgentInfoService", () => ({
  AgentInfoService: {
    getAllAgentInfos: vi.fn(() => ({
      block: () => Promise.resolve(MOCK_AGENTS),
    })),
  },
}));
```

##### Pattern C: Shared Mock Data Module

For components with multiple endpoint dependencies, extract mock data to a shared module:

```ts
// src/main/frontend/mocks/agent-mocks.ts
import type { AgentInfo } from "Frontend/generated/types";

export const MOCK_AGENTS: AgentInfo[] = [
  {
    id: "mock-agent-1",
    definition: {
      title: "File Processor",
      agentType: "Map",
      fileInputRegex: "\\.txt$",
      targetDirectory: "/tmp/output",
    },
    source: "local",
    scannerId: "scan-001",
    createdAt: "2025-01-15T10:00:00Z",
    active: true,
  },
];

export const MOCK_SCANNERS = [
  {
    id: "scan-001",
    name: "Source Scanner",
    targetDirectory: "/tmp/source",
    lastRun: "2025-04-01T08:00:00Z",
  },
];
```

```tsx
// AgentList.stories.tsx
import { MOCK_AGENTS } from "../../mocks/agent-mocks";

vi.mock("Frontend/generated/endpoints", async (importOriginal) => {
  const actual = await importOriginal<typeof import("Frontend/generated/endpoints")>();
  return {
    ...actual,
    AgentInfoService: {
      getAllAgentInfos: vi.fn(() => ({ block: () => Promise.resolve(MOCK_AGENTS) })),
    },
  };
});
```

##### Pattern D: Mock with Story-Level Overrides

When different stories need different data, override the mock inside a `play` function:

```tsx
export const WithThreeAgents: Story = {
  play: async () => {
    vi.mocked(AgentInfoService.getAllAgentInfos).mockReturnValue({
      block: () =>
        Promise.resolve([
          ...MOCK_AGENTS,
          { id: "mock-agent-3", definition: { title: "Third Agent" }, active: true },
        ]),
    });
  },
};
```

##### Key Rules for `vi.mock()`

1. **`vi.mock()` must be at the top level** of the story file — it does not work inside `play` functions or `useEffect`.
2. **Use `vi.mocked()`** to access the mock with proper TypeScript typing when calling `.mockReturnValue()` or `.mockRejectedValue()`.
3. **Use `await importOriginal()`** to preserve other exports from the real module and avoid breaking unrelated functionality.
4. **Reset mocks between stories** using the `mockReset: true` parameter in the meta — Storybook v10 calls `.mockReset()` on all `vi.fn()` spies when a story unmounts.
5. **Match the exact import path** — the path in `vi.mock()` must match the `import` statement in the component, including whether it uses `.js` extension or not.
6. **Preserve the return shape** — Hilla endpoint methods return RxJS-style Observables (`.block()`, `.subscribe()`). Mock both to avoid runtime errors.

##### Why `vi.mock()` over other approaches?

| Approach | Server Required | Setup Complexity | Type Safety | Hot Reload |
|----------|----------------|------------------|-------------|------------|
| **`vi.mock()`** (preferred) | ❌ No | Low — inline in story | ✅ Full TypeScript | ✅ Instant |
| DI / prop injection | ❌ No | Medium — refactor component API | ✅ Full TypeScript | ✅ Instant |
| MSW (Mock Service Worker) | ❌ No | High — install, init, handlers file | ⚠️ Partial | ✅ Fast |
| Vite proxy to dev server | ✅ Yes | Medium — config + server running | ✅ Full | ⚠️ Server startup |

`vi.mock()` is the default because:
- **Zero infrastructure** — no server, no proxy, no extra packages
- **Full type safety** — mocks are typed via the original module's TypeScript definitions
- **Story-level granularity** — each story can have its own mock data without touching the component
- **Instant feedback** — Vite HMR reloads mock changes in milliseconds, no server boot required

## Consequences

### Benefits

- **Isolated component development** — Build and inspect components without running the Spring Boot server or navigating routes.
- **Interactive prop controls** — Toggle boolean flags, change text, select enum values live in the browser.
- **Auto-generated documentation** — Props table, JSDoc descriptions, and source code rendered from the component definition.
- **Fast feedback loop** — Vite HMR reloads stories in milliseconds, no server restart needed.
- **Static artifact** — `storybook build` produces a deployable HTML site for offline review or CI artifact inspection.
- **Built-in testing** — `storybook/test` provides `expect`, `userEvent`, and `spy` without extra packages.
- **Shared theme** — Storybook includes the Vaadin theme folder via `staticDirs`, so Lumo CSS custom properties and component styles render identically.

### Trade-offs

- **Extra dev dependency** — `storybook`, `@storybook/react-vite`, `@storybook/addon-docs` add ~150 packages to `node_modules`.
- **Two build systems** — The project already uses Vite (via Vaadin/Hilla). Storybook runs a second Vite instance on port 6006. However, with `vi.mock()` service mocking, Storybook is fully self-contained and does not require the Spring Boot server to run.
- **Not a replacement for E2E** — Storybook tests components in isolation. Playwright still needed for full-page integration (routing, navigation, backend calls).
- **Version gap risk** — Storybook v10 is the latest but some community addons may lag behind. We use only official packages (core + `addon-docs`).
- **Mock drift** — Service mocks in stories can become stale if the generated endpoint API changes without updating the corresponding stories. This is mitigated by keeping mock data close to the component (co-located in the `.stories.tsx` file) and reviewing story failures during endpoint regeneration.

### Tight Coupling Points

> ⚠️ Storybook depends on the project's `tsconfig.json` path aliases (`Frontend/*`). If these change, Storybook may fail to resolve imports.

> ⚠️ Storybook v10 merged `@storybook/addon-essentials` and `@storybook/test` into core. The old npm packages still exist at v8 and will be resolved if explicitly installed, causing version conflicts. **Never install `@storybook/addon-essentials` or `@storybook/test` separately** — they are built into `storybook@^10`.

### Important Notes

- Storybook runs on port **6006** by default. The Spring Boot dev server runs on port **8080**. They do not conflict.
- The `staticDirs` config includes `src/main/frontend/themes/` so Vaadin Lumo CSS custom properties (e.g., `--lumo-success-color`) are available in Storybook previews.
- Components using `@vaadin/hilla-react-signals` (`useSignal`) work correctly in Storybook because they are pure React hooks with no server dependency.
- Components that call Hilla endpoints (generated in `Frontend/generated/endpoints.ts`) should be mocked using `vi.mock()` in the story file — **no running server required**.
- The `Frontend/generated/endpoints.ts` module is regenerated on every `mvn vaadin:build-frontend` run. Mocks must match the current generated API shape. If the endpoint signature changes, update the corresponding story mocks.
- For Flow views (Java `@Route` components), Storybook cannot render them — they require the full Vaadin server. Use **BrowserlessTest** for Flow component testing and **Playwright E2E** for full-page integration. See [ADR-003](adr-003-vaadin-hilla-ui.md) and [ADR-004](adr-004-flow-hilla-routing.md).
- **Graceful degradation workaround** — Components that call Hilla endpoints can use `try/catch` with local fallback state updates instead of `vi.mock()`. This is demonstrated by the `Counter` component in the layout drawer. Use this for simple stateful components or demo widgets; prefer `vi.mock()` for complex components with multiple endpoint calls. See the "Service Mocking Workaround" section above.

## See Also

- [ADR-003: Vaadin/Hilla UI Components](adr-003-vaadin-hilla-ui.md) — Java-side Vaadin components and views
- [ADR-004: Flow/Hilla Routing](adr-004-flow-hilla-routing.md) — View routing, Hilla/Flow coexistence, navigation layout

## References

- [Storybook Documentation](https://storybook.js.org/docs) — Official docs for configuration, stories, addons
- [Storybook GitHub Repository](https://github.com/storybookjs/storybook) — Source code and migration guides
- [Storybook Migration Guide (v9 → v10)](https://github.com/storybookjs/storybook/blob/next/MIGRATION.md) — Documents `addon-essentials` removal and core consolidation
- [Vaadin Hilla Testing Guide](https://vaadin.com/docs/latest/hilla/guides/testing) — Vitest + React Testing Library for Hilla components
- [React Testing Library](https://testing-library.com/docs/react-testing-library/intro/) — Reference for `render`, `screen`, `userEvent` patterns

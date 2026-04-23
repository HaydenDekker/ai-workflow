# ADR-004: Hilla UI Component Development with Storybook

## Date

2026-04-23

## Context

The application uses **Vaadin 25 with Hilla** for its UI layer ([ADR-002](adr-ui-components.md), [ADR-003](adr-ui-views.md)). Views are written as Java classes with `@Route`, and reusable components are Java-side Vaadin components. However, the application also has a **Hilla frontend layer** (`src/main/frontend/`) where React views and components live (e.g., the `HomeView.tsx` index view).

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

#### 1. Creating a New Component

```tsx
// src/main/frontend/components/StatusBadge.tsx
export interface StatusBadgeProps {
  status: "up" | "down" | "warn";
  label: string;
}

export function StatusBadge({ status, label }: StatusBadgeProps) {
  const color = status === "up" ? "green" : status === "down" ? "red" : "orange";
  return (
    <span style={{ color, fontWeight: "bold" }}>
      {label} ({status})
    </span>
  );
}
```

#### 2. Writing Stories (co-located)

```tsx
// src/main/frontend/components/StatusBadge.stories.tsx
import type { Meta, StoryObj } from "@storybook/react";
import { StatusBadge } from "./StatusBadge";

export default {
  title: "Components/StatusBadge",
  component: StatusBadge,
  tags: ["autodocs"],
  argTypes: {
    status: { control: "select", options: ["up", "down", "warn"] },
    label: { control: "text" },
  },
} satisfies Meta<typeof StatusBadge>;

type Story = StoryObj<typeof StatusBadge>;

export const Up: Story = { args: { status: "up", label: "Service A" } };
export const Down: Story = { args: { status: "down", label: "Service B" } };
export const Warn: Story = { args: { status: "warn", label: "Service C" } };
```

#### 3. Running and Inspecting

```bash
npm run storybook
# Opens http://localhost:6006
# - Sidebar shows story tree
# - Controls panel lets you change props live
# - Docs panel shows auto-generated props table
```

#### 4. Building Static Output

```bash
npm run build-storybook
# Outputs to storybook-static/
# Can be served with any static file server or committed to a branch for review
```

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

> **Note:** Browserless testing (`BrowserlessTest`) is the preferred approach for testing Java-side Flow components and views. It runs 100× faster than Playwright and provides direct Java API access. See [ADR-002](adr-ui-components.md) for details.

### Component Conventions

#### 1. Co-location

Stories live next to components (`Component.tsx` + `Component.stories.tsx`). Storybook auto-discovers them via the glob pattern in `main.ts`.

#### 2. Props Interface

Every component exports a typed `Props` interface with JSDoc comments. Storybook's docs panel generates the props table from these types.

#### 3. Default Story Args

The `Default` story should represent the most common usage. Other stories cover edge cases and variants.

#### 4. Auto-docs Tag

Include `tags: ["autodocs"]` in the meta to enable the docs panel with props table, description, and source code.

#### 5. No Backend Calls in Stories

Components that call Hilla endpoints (e.g., `HelloWorldService.sayHello()`) should either:
- Accept the service call as a prop (dependency injection) and pass a mock in stories
- Use the `play` function to intercept and verify calls
- Skip the call behind a feature flag or optional prop

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
- **Two build systems** — The project already uses Vite (via Vaadin/Hilla). Storybook runs a second Vite instance on port 6006.
- **Not a replacement for E2E** — Storybook tests components in isolation. Playwright still needed for full-page integration (routing, navigation, backend calls).
- **Version gap risk** — Storybook v10 is the latest but some community addons may lag behind. We use only official packages (core + `addon-docs`).

### Tight Coupling Points

> ⚠️ Storybook depends on the project's `tsconfig.json` path aliases (`Frontend/*`). If these change, Storybook may fail to resolve imports.

> ⚠️ Storybook v10 merged `@storybook/addon-essentials` and `@storybook/test` into core. The old npm packages still exist at v8 and will be resolved if explicitly installed, causing version conflicts. **Never install `@storybook/addon-essentials` or `@storybook/test` separately** — they are built into `storybook@^10`.

### Important Notes

- Storybook runs on port **6006** by default. The Spring Boot dev server runs on port **8080**. They do not conflict.
- The `staticDirs` config includes `src/main/frontend/themes/` so Vaadin Lumo CSS custom properties (e.g., `--lumo-success-color`) are available in Storybook previews.
- Components using `@vaadin/hilla-react-signals` (`useSignal`) work correctly in Storybook because they are pure React hooks with no server dependency.
- Components that call Hilla endpoints (generated in `Frontend/generated/endpoints.ts`) will fail if called without a running backend. These should be mocked or accept the service as a prop.

## See Also

- [ADR-002: Vaadin/Hilla UI Components](adr-ui-components.md) — Java-side Vaadin components and views
- [ADR-003: UI Views and Routing](adr-ui-views.md) — View routing, Hilla/Flow coexistence, navigation layout
- [ADR: Hilla Setup Guide](adr-hilla-setup-guide.md) — Hilla configuration and project initialization

## References

- [Storybook Documentation](https://storybook.js.org/docs) — Official docs for configuration, stories, addons
- [Storybook GitHub Repository](https://github.com/storybookjs/storybook) — Source code and migration guides
- [Storybook Migration Guide (v9 → v10)](https://github.com/storybookjs/storybook/blob/next/MIGRATION.md) — Documents `addon-essentials` removal and core consolidation
- [Vaadin Hilla Testing Guide](https://vaadin.com/docs/latest/hilla/guides/testing) — Vitest + React Testing Library for Hilla components
- [React Testing Library](https://testing-library.com/docs/react-testing-library/intro/) — Reference for `render`, `screen`, `userEvent` patterns

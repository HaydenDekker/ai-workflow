# Plan: Agent Creation Modal + Test View

**Date**: 2026-04-21  
**References**: ADR-002 (Vaadin/Hilla UI Components), ADR-003 (UI Views and Routing)

---

## 1. Goal

Implement a **modal dialog** for creating new agents from the Agent ListView, plus a **dedicated test view** (`/agents/create/test`) that allows rapid prototyping with dummy data and visual confirmation via notification.

---

## 2. Architecture Decisions (per ADRs)

| Decision | Choice | Rationale |
|----------|--------|-----------|
| **Modal implementation** | Vaadin `Dialog` with `FormLayout` | Per ADR-002, Vaadin pre-built dialogs are the standard pattern. `Dialog` + `FormLayout` gives us form validation, keyboard support, and proper lifecycle handling. |
| **Test view approach** | Separate Vaadin `@Route` Flow view | Per ADR-003, Flow views use `@Route` annotations. A dedicated test view keeps prototype work isolated from production views and doesn't need to be added to `@layout.tsx` nav bar. |
| **Service layer** | Extend `AgentInfoService` | Per ADR-002, the UI service layer is the single integration point between views and backend. Add `createAgent()` method here. |
| **E2E testing** | Playwright (`*.spec.ts`) | Per ADR-002 testing strategy, E2E tests use Playwright with real Chromium. |
| **Styling** | Lumo CSS custom properties + `addClassName()` | Per ADR-002, all component styles use Lumo variables (`--lumo-success-color`, `--lumo-space-m`, etc.) and CSS class names applied via `addClassName()`. |

---

## 3. Files to Create

```
src/main/java/com/hdekker/ai_workflow/
├── ui/
│   ├── components/
│   │   └── AgentCreationDialog.java          # NEW — reusable modal dialog
│   └── views/
│       └── AgentCreationTestView.java        # NEW — dedicated test view
├── service/
│   └── AgentInfoService.java                 # MODIFY — add createAgent()
```

## 4. Files to Modify

```
src/main/java/com/hdekker/ai_workflow/
├── ui/
│   └── views/
│       └── AgentListView.java                # MODIFY — wire dialog to "New Agent" button
└── rest/
    └── AgentInfoService.java                 # MODIFY — add createAgent() method
```

---

## 5. Component Design: `AgentCreationDialog`

### 5.1 Class Structure

```
AgentCreationDialog (extends Dialog)
├── FormLayout formLayout
│   ├── TextField titleField          (required, max 100 chars)
│   ├── TextField fileInputRegexField (required, Vaadin regex validator)
│   ├── ComboBox<String> agentTypeCombo  (Map / Reduction / Split — required)
│   ├── TextArea bodyField            (required, grows with content)
│   ├── TextArea outputStructureField (required, grows with content)
│   ├── TextField outputFilenameTemplateField (required)
│   └── HorizontalLayout buttonBar    (Cancel | Create buttons)
├── String createdAgentId (set on confirm)
├── Consumer<AgentDefinition> onConfirm (callback)
└── void open(AgentDefinition existing) — for future edit support
```

### 5.2 Form Layout

```
┌─────────────────────────────────────────────────────┐
│  Create New Agent                        [×]        │
├─────────────────────────────────────────────────────┤
│                                                     │
│  Title              [__________________________]    │
│                                                     │
│  File Input Regex   [__________________________]    │
│                     Pattern: (?:(.*/)?)(.*)         │
│                                                     │
│  Agent Type         ▼ Select agent type            │
│                     ☐ Map                           │
│                     ☐ Reduction                     │
│                     ☐ Split                         │
│                                                     │
│  Body (Prompt)      [__________________________]    │
│                      |                          |   │
│                      |                          |   │
│                      |__________________________|   │
│                                                     │
│  Output Structure   [__________________________]    │
│                                                     │
│  Output Filename    [__________________________]    │
│  Template                                           │
│                                                     │
├─────────────────────────────────────────────────────┤
│                           [Cancel]    [Create Agent] │
└─────────────────────────────────────────────────────┘
```

### 5.3 Validation

| Field | Required | Validation | Error Message |
|-------|----------|------------|---------------|
| `title` | Yes | Not blank, max 100 chars | "Title is required" |
| `fileInputRegex` | Yes | Valid Java regex | "Invalid regex pattern" |
| `agentType` | Yes | Must be one of: Map, Reduction, Split | "Agent type is required" |
| `body` | Yes | Not blank | "Body is required" |
| `outputStructure` | Yes | Not blank | "Output structure is required" |
| `outputFilenameTemplate` | Yes | Not blank | "Output filename template is required" |

### 5.4 Behavior

1. **Open** → Dialog becomes modal, focus moves to `titleField`, overlay blocks background
2. **Cancel / Escape / Click overlay** → Dialog closes, form reset
3. **Create Agent** → Validate all fields → If valid, call `onAccept` callback with populated `AgentDefinition` → Show success notification → Close dialog → Reset form
4. **Create Agent (invalid)** → Show error notification, keep dialog open with red-highlighted fields

---

## 6. Test View Design: `AgentCreationTestView`

### 6.1 Purpose

A self-contained prototype view that lets developers **rapidly test the agent creation dialog** with dummy data, without needing the full backend pipeline running.

### 6.2 Route

```java
@Route("agents/create/test")
@PageTitle("Agent Creation Test")
```

**Note**: Per ADR-003, this view does **not** need to be added to `@layout.tsx` `flowRoutes` array — it's a test-only view accessed directly via URL.

### 6.3 Layout

```
┌─────────────────────────────────────────────────────┐
│  Agent Creation Test View                           │
├─────────────────────────────────────────────────────┤
│                                                     │
│  [Open Agent Creation Dialog]                       │
│                                                     │
│  ─── Form Preview ────────────────────────────────  │
│  Title:              [dummy title]                  │
│  File Input Regex:   [.*\.java]                     │
│  Agent Type:         Map                            │
│  Body:               [Process Java files...]        │
│  Output Structure:   [Generate summary...]          │
│  Output Filename:    output/${name}.md              │
│                                                     │
│  [Simulate API Call (POST /api/agents)]             │
│                                                     │
│  ─── Response ────────────────────────────────────  │
│  Status: 200 OK                                     │
│  Agent ID: abc123-def456                           │
│  Source: DYNAMIC                                    │
│                                                     │
└─────────────────────────────────────────────────────┘
```

### 6.4 Behavior

| Action | Result |
|--------|--------|
| Click **"Open Agent Creation Dialog"** | Opens `AgentCreationDialog` pre-populated with dummy data. On submit, shows notification confirming the `AgentDefinition` that would be sent. |
| Click **"Simulate API Call"** | Constructs an `AgentDefinition` from current form values, sends `POST /api/agents` via `VaadinRestClient` (or `RestClient`), shows response in the result area. |
| Form fields | Editable — all fields have default dummy values pre-filled. |

### 6.5 Notification Confirmation

On successful dialog submission (test view mode), show:

```java
Notification.show(
    "Agent created: " + agentDefinition.title(),
    "Type: " + agentDefinition.agentType() + 
    " | Regex: " + agentDefinition.fileInputRegex(),
    Notification.Position.MIDDLE,
    5000
);
```

---

## 7. Service Layer Changes

### 7.1 `AgentInfoService` — Add `createAgent`

```java
public Mono<AgentInfo> createAgent(AgentDefinition agentDefinition) {
    try {
        AgentInfo info = dynamicAgentManager.addDynamicAgent(agentDefinition);
        return Mono.just(info);
    } catch (Exception ex) {
        log.error("Error creating agent: {}", agentDefinition.title(), ex);
        return Mono.error(ex);
    }
}
```

### 7.2 `AgentListView` — Wire Dialog to "New Agent" Button

Replace the placeholder:

```java
// OLD (placeholder)
Button createButton = new Button("New Agent", event -> {
    Notification.show("Create new Agent dialog will open here");
});

// NEW
Button createButton = new Button("New Agent", event -> {
    AgentCreationDialog dialog = new AgentCreationDialog(agentInfoService);
    dialog.addCloseListener(e -> reloadData());
    dialog.open();
});
```

---

## 8. E2E Tests

### 8.1 Test File: `tests/e2e/agent-creation.spec.ts`

```typescript
import { test, expect } from '@playwright/test';

test.describe('Agent Creation Dialog', () => {
  test('Agent Creation Test View loads', async ({ page }) => {
    await page.goto('/agents/create/test');
    await page.waitForTimeout(3000);
    
    // Verify page title
    await expect(page).toHaveTitle(/Agent Creation Test/i);
    
    // Verify dialog button exists
    const dialogButton = page.locator('button:has-text("Open Agent Creation Dialog")');
    await expect(dialogButton).toBeVisible();
  });

  test('Dialog opens with all required fields', async ({ page }) => {
    await page.goto('/agents/create/test');
    await page.waitForTimeout(3000);
    
    // Open dialog
    await page.locator('button:has-text("Open Agent Creation Dialog")').click();
    await page.waitForTimeout(1000);
    
    // Verify dialog is visible
    const dialog = page.locator('vaadin-dialog-overlay');
    await expect(dialog).toBeVisible();
    
    // Verify all fields are present
    await expect(page.locator('vaadin-text-field[label="Title"]')).toBeVisible();
    await expect(page.locator('vaadin-text-field[label="File Input Regex"]')).toBeVisible();
    await expect(page.locator('vaadin-combo-box[label="Agent Type"]')).toBeVisible();
    await expect(page.locator('vaadin-text-area[label="Body (Prompt)"]')).toBeVisible();
    await expect(page.locator('vaadin-text-area[label="Output Structure"]')).toBeVisible();
    await expect(page.locator('vaadin-text-field[label="Output Filename Template"]')).toBeVisible();
  });

  test('Dialog accepts input in all fields', async ({ page }) => {
    await page.goto('/agents/create/test');
    await page.waitForTimeout(3000);
    
    await page.locator('button:has-text("Open Agent Creation Dialog")').click();
    await page.waitForTimeout(1000);
    
    // Fill all fields
    await page.locator('vaadin-text-field[label="Title"]').fill('Test Agent');
    await page.locator('vaadin-text-field[label="File Input Regex"]').fill('.*\\.java');
    await page.locator('vaadin-combo-box[label="Agent Type"]').click();
    await page.locator('vaadin-item:text("Map")').click();
    await page.locator('vaadin-text-area[label="Body (Prompt)"]').fill('Process Java files');
    await page.locator('vaadin-text-area[label="Output Structure"]').fill('Generate documentation');
    await page.locator('vaadin-text-field[label="Output Filename Template"]').fill('output/${name}.md');
    
    // Click Create button
    await page.locator('vaadin-button:has-text("Create Agent")').click();
    await page.waitForTimeout(1000);
    
    // Verify notification was shown
    const notification = page.locator('vaadin-notification-card');
    await expect(notification).toBeVisible();
  });

  test('Dialog closes on Cancel', async ({ page }) => {
    await page.goto('/agents/create/test');
    await page.waitForTimeout(3000);
    
    await page.locator('button:has-text("Open Agent Creation Dialog")').click();
    await page.waitForTimeout(1000);
    
    await page.locator('vaadin-button:has-text("Cancel")').click();
    
    // Verify dialog is closed
    const dialog = page.locator('vaadin-dialog-overlay');
    await expect(dialog).toBeHidden();
  });

  test('Simulate API Call sends POST request', async ({ page }) => {
    await page.goto('/agents/create/test');
    await page.waitForTimeout(3000);
    
    // Wait for API call interception
    const apiResponse = page.waitForResponse(response => 
      response.url().includes('/api/agents') && response.request().method() === 'POST'
    );
    
    await page.locator('button:has-text("Simulate API Call")').click();
    
    const response = await apiResponse;
    expect(response.status()).toBe(200);
  });

  test('Production: New Agent button opens dialog from Agent List', async ({ page }) => {
    await page.goto('/agents');
    await page.waitForTimeout(3000);
    
    // Click New Agent button
    await page.locator('vaadin-button:has-text("New Agent")').click();
    await page.waitForTimeout(1000);
    
    // Verify dialog is visible
    const dialog = page.locator('vaadin-dialog-overlay');
    await expect(dialog).toBeVisible();
  });
});
```

---

## 9. Implementation Order

| Step | Task | Estimated Effort | Depends On |
|------|------|-----------------|------------|
| **1** | Extend `AgentInfoService` with `createAgent()` | 30 min | None |
| **2** | Create `AgentCreationDialog.java` | 2 hours | None |
| **3** | Wire dialog into `AgentListView` "New Agent" button | 30 min | Steps 1, 2 |
| **4** | Create `AgentCreationTestView.java` | 1.5 hours | Steps 1, 2 |
| **5** | Add CSS styling in `styles.css` (dialog, form) | 30 min | Step 2 |
| **6** | Write Playwright E2E tests | 2 hours | Steps 3, 4 |
| **7** | Run `npm run test:e2e` to verify all tests pass | 15 min | Step 6 |

**Total estimated effort: ~6 hours**

---

## 10. Conventions Followed (from ADRs)

| ADR-002 Rule | How Applied |
|--------------|-------------|
| **Lifecycle-aware scheduling** | N/A for dialog (no schedulers), but `AgentCreationTestView` would follow `onDetach()` cleanup if added |
| **UI thread safety** | All Vaadin component updates happen on UI thread (standard for Flow views) |
| **CSS theme organization** | Dialog/form styles use `addClassName()` + Lumo variables in `styles.css` |
| **Component structure** | Dialog in `ui/components/`, views in `ui/views/` |
| **Testing strategy** | Unit (mock services) + E2E (Playwright) tiers |

| ADR-003 Rule | How Applied |
|--------------|-------------|
| **Flow-managed routes** | Both `AgentCreationDialog` and `AgentCreationTestView` use Vaadin Flow (`@Route`) |
| **Test view isolation** | `AgentCreationTestView` at `agents/create/test` — not added to `@layout.tsx` nav bar |
| **Path naming consistency** | Route path `agents/create/test` matches `@Route("agents/create/test")` |

---

## 11. Open Questions

| Question | Status |
|----------|--------|
| Should the dialog support editing existing agents (pre-fill from `AgentInfo`)? | Out of scope for this iteration — marked as future enhancement |
| Should `AgentCreationTestView` be added to `@layout.tsx` navigation? | No — per ADR-003, test views don't need nav entries. Accessed directly via URL |
| Should form validation show inline errors or toast notifications? | Inline errors on invalid fields + toast on submit for consistency with Vaadin patterns |
| Should the test view mock the API call or make a real POST? | Both — "Simulate API Call" button makes a real POST to `/api/agents`. Dialog submit in test view mode shows a notification with the JSON payload for quick verification |

---

*Plan ready for implementation.*

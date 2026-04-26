# Plan: Agent Detail Dialog (Edit + Delete)

**Date**: 2026-04-25  
**References**: 
- [ADR-002](../adrs/adr-ui-components.md) — Vaadin/Hilla UI Components, service layer ownership, reactive patterns
- [ADR-003](../adrs/adr-ui-views.md) — UI Views and Routing, view lifecycle, browserless testing
- `agent-creation-dialog-plan.md` — Previous agent creation flow (reference for dialog patterns)

### ADR-002 Key Rules Referenced

| Rule | Section | Relevance |
|------|---------|-----------|
| **Components cannot access services** | Component Building Principles §Service Access Boundary | `AgentDetailDialog` must not call `AgentInfoService` directly |
| **Views coordinate component state and services** | Component Building Principles §Service Access Boundary | `AgentListView` owns all reactive chains and `UI.access()` calls |
| **Reactive data loading pattern** | Component Building Principles §3 | `Mono<Void>`/`Mono.empty()` never triggers `subscribe()` callbacks |
| **UI thread safety** | Component Building Principles §2 | All Vaadin updates wrapped in `UI.getCurrent().access()` |
| **Lifecycle-aware scheduling** | Component Building Principles §1 | Schedulers stopped in `onDetach()` |

### ADR-003 Key Rules Referenced

| Rule | Section | Relevance |
|------|---------|-----------|
| **Flow-managed routes** | Architecture Overview | Views are `@Route`-annotated Java classes |
| **Testing strategy** | Testing Strategy | Browserless tests preferred for component testing; Playwright for critical flows |
| **Browserless testing pattern** | Browserless Testing | `runUIQueue()` + `roundTrip()` for reactive callback processing |

---

## 1. Goal

Enable users to **edit** and **delete** agents from the Agent ListView. Clicking on an agent row (or an action button) opens a **detail dialog** displaying all editable fields with **Save** and **Delete** buttons. On delete, the UI sends a `DELETE /api/agents/{id}` request and refreshes the grid.

---

## 2. Architecture Decisions (per ADRs)

| Decision | Choice | Rationale |
|----------|--------|-----------|
| **Detail dialog** | New `AgentDetailDialog` (extends `Dialog`) | Reuses the same Vaadin `Dialog` + `FormLayout` pattern as `AgentCreationDialog` (ADR-002). Keeps edit/delete concerns separate from creation. |
| **Trigger** | Click on agent row in the grid | Intuitive — clicking an agent opens its details for editing. |
| **Backend** | Extend existing REST endpoints | `PUT /api/agents/{id}` already works via `refreshAgent` for re-persisting; `DELETE /api/agents/{id}` already exists. No new backend endpoints required. |
| **Scanner cleanup** | Handled by `DynamicAgentManager.removeAgent()` | Already implemented — calls `scannerRegistry.destroyForAgent()` for one-to-one cleanup. No additional backend changes needed. |
| **Persistence** | Re-save full `AgentDefinition` on edit | `AgentPersistenceService.save()` upserts by ID. On edit, send the full updated definition back via `PUT`. |

---

## 3. Files to Create

```
src/main/java/com/hdekker/ai_workflow/
├── ui/
│   └── components/
│       └── AgentDetailDialog.java          # NEW — edit + delete dialog
```

## 4. Files to Modify

```
src/main/java/com/hdekker/ai_workflow/
├── ui/
│   └── views/
│       └── AgentListView.java              # MODIFY — add row click → detail dialog
├── ui/
│   └── service/
│       └── AgentInfoService.java           # MODIFY — add updateAgent() method
└── rest/
    └── AgentRestController.java            # MODIFY — add PUT /{id} endpoint
```

---

## 5. Component Design: `AgentDetailDialog`

### 5.1 Class Structure

```
AgentDetailDialog (extends Dialog)
├── FormLayout formLayout
│   ├── TextField titleField            (required, max 100 chars)
│   ├── TextField targetDirectoryField   (required, absolute path validator)
│   ├── TextField fileInputRegexField    (required, regex validator)
│   ├── ComboBox<String> agentTypeCombo  (Map / Reduction / Split — required)
│   ├── TextArea bodyField              (required, grows with content)
│   ├── TextArea outputStructureField   (required, grows with content)
│   ├── TextField outputFilenameTemplateField (required)
│   └── HorizontalLayout buttonBar      (Cancel | Save | Delete buttons)
├── AgentInfo existingAgent             (pre-filled on open)
├── AgentInfoService agentInfoService
├── Consumer<AgentInfo> onSave          (callback after save)
├── Consumer<String> onDelete           (callback after delete, receives agent id)
└── void open(AgentInfo agentInfo)      — pre-populates form, opens dialog
```

### 5.2 Form Layout

```
┌──────────────────────────────────────────────────────────────┐
│  Edit Agent: my-agent-id                     [×]            │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  Title              [__________________________]             │
│                                                              │
│  Target Directory   [__________________________]             │
│                     /data/inbox/documents                    │
│                                                              │
│  File Input Regex   [__________________________]             │
│                     .*\.java                                 │
│                                                              │
│  Agent Type         ▼ Map                                   │
│                     ☐ Map                                   │
│                     ☐ Reduction                             │
│                     ☐ Split                                 │
│                                                              │
│  Body (Prompt)      [__________________________]             │
│                      |                              |       │
│                      |                              |       │
│                      |______________________________|       │
│                                                              │
│  Output Structure   [__________________________]             │
│                     [Generate summary...]                    │
│                                                              │
│  Output Filename    [__________________________]             │
│  Template                 output/${name}.md                  │
│                                                              │
│  ─── Read Only ──────────────────────────────────────────   │
│  Created:    2026-04-25 10:30:15                            │
│  Active:     ☑ Yes                                          │
│  Source:     DYNAMIC                                        │
│  Scanner:    a1b2c3d4...                                    │
│                                                              │
├──────────────────────────────────────────────────────────────┤
│                    [Cancel]  [Save]    [Delete Agent]       │
└──────────────────────────────────────────────────────────────┘
```

### 5.3 Validation

Same rules as `AgentCreationDialog`:

| Field | Required | Validation | Error Message |
|-------|----------|------------|---------------|
| `title` | Yes | Not blank, max 100 chars | "Title is required" |
| `targetDirectory` | Yes | Absolute path, exists, is directory, readable | "Target directory is required", "Must be an absolute path", "Directory does not exist" |
| `fileInputRegex` | Yes | Valid Java regex | "Invalid regex pattern" |
| `agentType` | Yes | One of: Map, Reduction, Split | "Agent type is required" |
| `body` | Yes | Not blank | "Body is required" |
| `outputStructure` | Yes | Not blank | "Output structure is required" |
| `outputFilenameTemplate` | Yes | Not blank | "Output filename template is required" |

### 5.4 Behavior

| Action | Result |
|--------|--------|
| **Open (with AgentInfo)** | Dialog becomes modal, all fields pre-populated from `AgentInfo.definition()`, "Created/Active/Source/Scanner" shown as read-only |
| **Open (null)** | Same as creation dialog — all fields blank (used for future "add agent from list" flow) |
| **Cancel / Escape / Click overlay** | Dialog closes, no changes persisted |
| **Save** | Validate all fields → If valid, build updated `AgentDefinition` → Call `PUT /api/agents/{id}` (new endpoint) → Show success notification → Close dialog → Refresh grid via `onSave` callback |
| **Save (invalid)** | Show error notification, keep dialog open with red-highlighted fields |
| **Delete** | Show confirmation prompt → If confirmed, call `DELETE /api/agents/{id}` → Show success notification → Close dialog → Refresh grid via `onDelete` callback |
| **Delete (error)** | Show error notification, keep dialog open |

---

## 6. Backend Changes

### 6.1 `AgentRestController` — Add `PUT /{id}` Endpoint

```java
@PutMapping("/{id}")
public ResponseEntity<AgentInfo> updateAgent(
        @PathVariable String id,
        @RequestBody AgentDefinition agentDefinition) {
    // Remove old agent and re-add with updated definition
    dynamicAgentManager.removeAgent(id);
    String targetDir = agentDefinition.targetDirectory() != null
            ? agentDefinition.targetDirectory()
            : "/tmp";
    AgentInfo agentInfo = dynamicAgentManager.addDynamicAgent(agentDefinition, targetDir);
    return ResponseEntity.ok(agentInfo);
}
```

> **Note**: This approach removes and re-creates the agent (which also re-creates the scanner). An alternative would be to add a dedicated `updateAgent()` method in `DynamicAgentManager`, but the remove+re-add pattern is simpler and ensures scanner state is fully refreshed.

### 6.2 `AgentInfoService` — Add `updateAgent`

```java
public Mono<AgentInfo> updateAgent(String id, AgentDefinition agentDefinition) {
    try {
        // The REST call goes through Vaadin's built-in HTTP client
        // This method delegates to the REST endpoint
        return Mono.error(new UnsupportedOperationException("Use REST client directly"));
        // Or better: add a direct manager method
    } catch (Exception ex) {
        log.error("Error updating agent with id: {}", id, ex);
        return Mono.error(ex);
    }
}
```

> **Simpler approach**: Since `AgentInfoService` is a thin wrapper, the UI can call the REST endpoint directly via Vaadin's `RestClient` (or add a manager-level update method). See Implementation Order below.

### 6.3 `DynamicAgentManager` — Scanner Cleanup Verification

The `removeAgent()` method **already** handles scanner cleanup:

```java
public void removeAgent(String id) {
    // ... removes from agentRegistry / dormantAgents ...
    
    // One-to-one: destroy scanner when agent is removed
    if (entry.scannerId() != null && scannerRegistry != null) {
        scannerRegistry.destroyForAgent(entry.scannerId());
    }
    
    // Delete from DB
    if (persistenceService != null) {
        persistenceService.deleteById(id);
    }
}
```

**No changes needed here.** The scanner is destroyed when the agent is removed.

### 6.4 `ScannerRegistry` — Already Has `destroyForAgent()`

The `destroyForAgent(String scannerId)` method already:
1. Removes the scanner from the internal `ConcurrentHashMap`
2. Calls `meta.scanner().destroy()` to clean up the Spring Integration flow and resources

**No changes needed here.**

---

## 7. UI Changes: `AgentListView`

### 7.1 Add Row Click Handler

Replace the existing refresh-only action column with a clickable row that opens the detail dialog:

```java
// Existing: refresh button column
grid.addComponentColumn(agent -> {
    Button refreshBtn = new Button(new Icon(VaadinIcon.REFRESH));
    // ...
});

// NEW: Add row click listener
grid.addItemClickListener(e -> {
    AgentDetailDialog dialog = new AgentDetailDialog(
            agentInfoService,
            e.getItem(),
            updatedInfo -> reloadData(),      // onSave
            deletedId -> reloadData()          // onDelete
    );
    dialog.open();
});
```

### 7.2 Optional: Keep Refresh Button as Separate Action

For clarity, the refresh button can remain as a separate column or be moved into a context menu. The primary interaction is clicking the row to open the detail dialog.

---

## 8. E2E Tests

### 8.1 Test File: `tests/e2e/agent-detail.spec.ts`

```typescript
import { test, expect } from '@playwright/test';

test.describe('Agent Detail Dialog', () => {
  test('Click on agent row opens detail dialog', async ({ page }) => {
    await page.goto('/agents');
    await page.waitForTimeout(3000);
    
    // Click on the first agent row
    const firstRow = page.locator('vaadin-grid-row').first();
    await firstRow.click();
    await page.waitForTimeout(1000);
    
    // Verify dialog is visible
    const dialog = page.locator('vaadin-dialog-overlay');
    await expect(dialog).toBeVisible();
    
    // Verify all fields are present and pre-populated
    await expect(page.locator('vaadin-text-field[label="Title"]')).toBeVisible();
    await expect(page.locator('vaadin-text-field[label="Target Directory"]')).toBeVisible();
    await expect(page.locator('vaadin-combo-box[label="Agent Type"]')).toBeVisible();
    await expect(page.locator('vaadin-button:has-text("Save")')).toBeVisible();
    await expect(page.locator('vaadin-button:has-text("Delete Agent")')).toBeVisible();
  });

  test('Delete agent via dialog', async ({ page }) => {
    await page.goto('/agents');
    await page.waitForTimeout(3000);
    
    // Click on an agent row
    const firstRow = page.locator('vaadin-grid-row').first();
    await firstRow.click();
    await page.waitForTimeout(1000);
    
    // Click Delete button
    await page.locator('vaadin-button:has-text("Delete Agent")').click();
    await page.waitForTimeout(500);
    
    // Confirm delete in confirmation dialog
    const confirmDialog = page.locator('vaadin-dialog-overlay');
    await expect(confirmDialog).toBeVisible();
    await page.locator('vaadin-button:has-text("Confirm")').click();
    await page.waitForTimeout(1000);
    
    // Verify dialog closed
    await expect(page.locator('vaadin-dialog-overlay')).toBeHidden();
    
    // Verify agent removed from grid
    const notification = page.locator('vaadin-notification-card');
    await expect(notification).toBeVisible();
  });

  test('Edit agent fields and save', async ({ page }) => {
    await page.goto('/agents');
    await page.waitForTimeout(3000);
    
    // Open detail dialog
    const firstRow = page.locator('vaadin-grid-row').first();
    await firstRow.click();
    await page.waitForTimeout(1000);
    
    // Edit title field
    await page.locator('vaadin-text-field[label="Title"]').fill('Updated Agent Title');
    
    // Click Save
    await page.locator('vaadin-button:has-text("Save")').click();
    await page.waitForTimeout(1000);
    
    // Verify success notification
    const notification = page.locator('vaadin-notification-card');
    await expect(notification).toBeVisible();
    
    // Verify dialog closed
    await expect(page.locator('vaadin-dialog-overlay')).toBeHidden();
  });

  test('Cancel closes dialog without changes', async ({ page }) => {
    await page.goto('/agents');
    await page.waitForTimeout(3000);
    
    // Open detail dialog
    const firstRow = page.locator('vaadin-grid-row').first();
    await firstRow.click();
    await page.waitForTimeout(1000);
    
    // Edit and cancel
    await page.locator('vaadin-text-field[label="Title"]').fill('Should Not Persist');
    await page.locator('vaadin-button:has-text("Cancel")').click();
    
    // Verify dialog closed
    await expect(page.locator('vaadin-dialog-overlay')).toBeHidden();
  });

  test('Validation errors shown on invalid save', async ({ page }) => {
    await page.goto('/agents');
    await page.waitForTimeout(3000);
    
    // Open detail dialog
    const firstRow = page.locator('vaadin-grid-row').first();
    await firstRow.click();
    await page.waitForTimeout(1000);
    
    // Clear required field
    await page.locator('vaadin-text-field[label="Title"]').fill('');
    
    // Click Save
    await page.locator('vaadin-button:has-text("Save")').click();
    await page.waitForTimeout(500);
    
    // Verify error notification
    const notification = page.locator('vaadin-notification-card');
    await expect(notification).toBeVisible();
    
    // Verify dialog still open
    await expect(page.locator('vaadin-dialog-overlay')).toBeVisible();
  });
});
```

---

## 9. Implementation Order

| Step | Task | Estimated Effort | Depends On |
|------|------|-----------------|------------|
| **1** | Add `PUT /api/agents/{id}` endpoint to `AgentRestController` | 30 min | None |
| **2** | Add `updateAgent(String id, AgentDefinition)` to `DynamicAgentManager` (remove + re-add pattern) | 30 min | None |
| **3** | Add `updateAgent(String id, AgentDefinition)` to `AgentInfoService` | 15 min | Step 2 |
| **4** | Create `AgentDetailDialog.java` (reuses `AgentCreationDialog` patterns) | 2 hours | Steps 1-3 |
| **5** | Wire dialog into `AgentListView` (add row click handler) | 30 min | Steps 1-4 |
| **6** | Add CSS styling in `styles.css` (dialog, form, delete button variant) | 30 min | Step 4 |
| **7** | Write Playwright E2E tests | 2 hours | Steps 5, 6 |
| **8** | Run `npm run test:e2e` to verify all tests pass | 15 min | Step 7 |

**Total estimated effort: ~5 hours**

---

## 10. Conventions Followed (from ADRs)

| ADR-002 Rule | How Applied |
|--------------|-------------|
| **Vaadin pre-built dialogs** | `AgentDetailDialog` extends `Dialog` with `FormLayout` — same pattern as `AgentCreationDialog` |
| **CSS theme organization** | Dialog/form styles use `addClassName()` + Lumo variables (`--lumo-error-color` for delete button) |
| **Component structure** | Dialog in `ui/components/`, views in `ui/views/` |
| **Lifecycle-aware scheduling** | N/A (no schedulers in dialog) |
| **UI thread safety** | All Vaadin component updates on UI thread (standard for Flow views) |
| **Testing strategy** | Unit (mock services) + E2E (Playwright) tiers |

| ADR-003 Rule | How Applied |
|--------------|-------------|
| **Flow-managed routes** | `AgentDetailDialog` is a component, not a route — used within `AgentListView` |
| **Path naming consistency** | N/A (dialog is not a route) |

---

## 11. Scanner Cleanup — Already Handled

The user requirement that *"the file scanner must also be removed in dynamic agent manager"* is **already implemented**:

| Layer | Method | Scanner Cleanup |
|-------|--------|-----------------|
| `DynamicAgentManager.removeAgent(String id)` | Active agent | `scannerRegistry.destroyForAgent(entry.scannerId())` |
| `DynamicAgentManager.removeAgent(String id)` | Dormant agent | `scannerRegistry.destroyForAgent(dormantEntry.scannerId())` |
| `ScannerRegistry.destroyForAgent(String scannerId)` | — | Removes from map + calls `scanner.destroy()` |

**No additional backend changes required for scanner cleanup.** The only missing piece is the UI dialog to trigger the delete.

---

## 12. Open Questions

| Question | Status |
|----------|--------|
| Should the delete button require a confirmation step? | Yes — use Vaadin `Dialog` confirmation or browser `confirm()` before sending DELETE |
| Should edit also re-create the scanner (like the refresh action)? | Yes — the remove+re-add pattern in `updateAgent()` re-creates the scanner, ensuring clean state |
| Should YAML agents be editable/deletable? | Out of scope for this iteration — only DYNAMIC agents should be editable/deletable (YAML agents are configuration-driven) |
| Should the dialog show scanner status (IDLE / EMITTING_ALL / etc.)? | Nice-to-have — add a read-only scanner status field in the "Read Only" section |

---

## 13. Implementation Status (Updated 2026-04-26)

### 13.1 Completed

| Step | Task | Status | Notes |
|------|------|--------|-------|
| **1** | Add `PUT /api/agents/{id}` endpoint to `AgentRestController` | ✅ Done | Uses remove+re-add pattern |
| **2** | Add `updateAgent(String id, AgentDefinition)` to `DynamicAgentManager` | ✅ Done | Remove + re-add scanner |
| **3** | Add `updateAgent(String id, AgentDefinition)` to `AgentInfoService` | ✅ Done | Delegates to manager |
| **4** | Create `AgentDetailDialog.java` | ✅ Done | Edit + delete dialog with form validation |
| **5** | Wire dialog into `AgentListView` (row click handler) | ✅ Done | With `UI.access()` callbacks |
| **6** | Add CSS styling | ✅ Done | Via `addClassName()` + Lumo themes |
| **7** | Write Vaadin browserless tests | ✅ Done | `PushDemoViewTest` + `AgentListViewDeleteTest` |

### 13.2 Bugs Found & Fixed

#### Bug 1: `reloadData()` never called after delete
**Symptom**: After deleting an agent, the grid showed the deleted agent row still present.

**Root Cause**: The callbacks in `AgentListView` used `UI.getCurrent().accessLater(() -> reloadData(), null)` — but `accessLater()` **returns a `Runnable`**, it does **not execute** it. The code was creating a runnable and discarding it.

```java
// Before (broken — runnable created but never called)
deletedId -> UI.getCurrent().accessLater(() -> reloadData(), null)

// After (fixed — callback queues reload on Vaadin UI thread)
deletedId -> UI.getCurrent().access(() -> reloadData())
```

#### Bug 2: Reactive `subscribe()` callback never fired
**Symptom**: Even after fixing Bug 1, the delete confirmation callback didn't trigger the grid refresh.

**Root Cause**: `AgentInfoService.deleteAgent()` returned `Mono.empty()`. In Reactor, `Mono.empty()` completes without emitting a value, so `subscribe(unused -> {...})` is **never invoked**. The callback consumer is only called when a value is emitted.

```java
// Before (broken — empty never triggers subscribe)
public Mono<Void> deleteAgent(String id) {
    dynamicAgentManager.removeAgent(id);
    return Mono.empty();  // never fires subscribe callback!
}

// After (fixed — emits the agent id, triggering the callback)
public Mono<String> deleteAgent(String id) {
    dynamicAgentManager.removeAgent(id);
    return Mono.just(id);  // emits value → subscribe fires
}
```

#### Bug 3: Reactive callback runs outside Vaadin UI context
**Symptom**: Even after fixing Bugs 1 & 2, the reload callback couldn't update the grid because `UI.getCurrent()` was null in the reactive callback thread.

**Root Cause**: `Mono.just(id).subscribe()` fires synchronously on the calling thread. The button click handler runs on the Vaadin UI thread, but the `performDelete()` call was made directly (not queued via `UI.access()`), so when the reactive callback fired, the Vaadin context was still available — however the subsequent `UI.access()` inside the callback was on a non-Vaadin thread.

**Fix**: Queue `performDelete()` via `UI.getCurrent().access(() -> performDelete())` in the confirm button handler, and inside the reactive callback, use `UI.getCurrent().access(() -> onDelete.accept(deletedId))` to queue the reload. Both callbacks execute on the Vaadin UI thread and trigger push when the session unlocks.

#### Bug 4 (Architectural): Service access inside UI component
**Symptom**: The `AgentDetailDialog` component directly called `AgentInfoService.deleteAgent()` and `.updateAgent()`, embedding reactive chains and Vaadin threading logic inside a reusable dialog component.

**Root Cause**: This violates the architecture documented in **ADR-002** (Vaadin/Hilla UI Components), which specifies that `AgentInfoService` belongs to the **View** layer, not the component layer. The ADR diagram shows services owned by views, with components communicating via callbacks.

```java
// Before (violates ADR-002 — component owns service)
public class AgentDetailDialog extends Dialog {
    private final AgentInfoService agentInfoService;  // ❌ component owns service
    
    private void handleDelete() {
        agentInfoService.deleteAgent(id).subscribe(...);  // ❌ reactive chain in component
    }
}

// After (ADR-002 compliant — view owns service)
public class AgentDetailDialog extends Dialog {
    private final Consumer<String> onDelete;  // ✅ just a callback
    
    private void handleDelete() {
        onDelete.accept(id);  // ✅ pure UI, no service dependency
    }
}

// AgentListView — owns service, controls threading
grid.addItemClickListener(e -> {
    new AgentDetailDialog(
        e.getItem(),
        updatedDef -> {
            // View controls reactive chain and threading
            agentInfoService.updateAgent(e.getItem().id(), updatedDef)
                .subscribe(info -> UI.getCurrent().access(() -> reloadData()));
        },
        id -> {
            agentInfoService.deleteAgent(id)
                .subscribe(deletedId -> UI.getCurrent().access(() -> reloadData()));
        }
    );
});
```

**Why this matters**:
- **Component testability**: Components can be tested without service mocks — just fire callbacks
- **Thread safety**: Reactive callbacks run on random threads; views control `UI.access()` wrapping
- **Bug prevention**: The `Mono.empty()` / `UI.getCurrent()` null bugs we hit are exactly the kind of problems that happen when service/async logic bleeds into components
- **Re-entrancy**: Views can gate or reorder service calls; components have no visibility into this

### 13.3 Files Changed This Session

| File | Change |
|------|--------|
| `AgentDetailView.java` (main) | Fixed delete callbacks, added `log.info("Loaded {} agents")` |
| `AgentDetailDialog.java` (main) | Fixed reactive flow: `Mono.just(id)`, queued UI access |
| `AgentInfoService.java` (main) | Changed `deleteAgent()` return type `Mono<Void>` → `Mono<String>` |
| `PushDemoView.java` (test) | **NEW** — mock view for push pattern prototyping |
| `PushDemoViewTest.java` (test) | **NEW** — 2 browserless tests demonstrating push pattern |
| `AgentListViewDeleteTest.java` (test) | **NEW** — 3 browserless tests for delete flow |

### 13.4 Playwright E2E Tests

The `tests/e2e/agent-detail.spec.ts` plan (Section 8) is **deferred** until the Vaadin push issue in Playwright is resolved. The browserless tests above serve as a prototype for the correct pattern:

```java
// Browserless test pattern (works):
MockVaadin.runUIQueue();   // processes UI.access() callbacks
roundTrip();               // flushes state tree to client

// Equivalent Playwright pattern (needs):
// Wait for Vaadin push to complete before asserting
// page.waitForResponse() or similar mechanism
```

### 13.5 Phase 2: Refactor Service Access

**Goal**: Move all `AgentInfoService` calls from `AgentDetailDialog` into `AgentListView`, per ADR-002 architecture. Components should communicate via callbacks, not direct service access.

| Step | Task | Estimated Effort | Depends On |
|------|------|-----------------|------------|
| **1** | Remove `AgentInfoService` dependency from `AgentDetailDialog` | 30 min | None |
| **2** | Change `AgentDetailDialog` constructor to accept `Consumer<AgentDefinition>` (onSave) and `Consumer<String>` (onDelete) | 30 min | Step 1 |
| **3** | Move reactive chains (`updateAgent`, `deleteAgent`) into `AgentListView` row click handler | 30 min | Steps 1-2 |
| **4** | Wrap all reactive callbacks in `UI.getCurrent().access()` inside `AgentListView` | 15 min | Step 3 |
| **5** | Update browserless test to work with new callback-based API | 30 min | Steps 1-4 |
| **6** | Run `./mvnw test` — all tests pass | 15 min | Step 5 |

**Total estimated effort: ~2 hours**

### 13.6 Remaining Work

| Item | Priority | Notes |
|------|----------|-------|
| Phase 2: Refactor service access | High | Violates ADR-002, caused multiple bugs |
| Playwright E2E tests | Medium | Defer until Phase 2 is complete — clean component API makes E2E easier |
| Scanner status in dialog | Low | Nice-to-have read-only field |
| CSS polish for dialog | Low | Delete button styling, form layout refinements |

---

*Plan updated 2026-04-26 — core functionality complete, Phase 2 (service access refactor) next.*

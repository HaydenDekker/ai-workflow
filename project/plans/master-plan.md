# Master Plan — Project Index

> **Last Updated:** 2026-05-11 (plan 12 added)

## How to Add a Plan Overview

Each plan file must contain a **title** (e.g. `# Plan: Plan Name`) and **date created** (e.g. `> **Created:** 2026-05-05`).

To add it to this index:

1. Add a row to the table below: number, link, status, date.
2. Optionally add a section below with **name** and **goal** only. The goal should state **what** the plan covers — not why or how. Full details belong in the plan file.

**Ordering:** Plans are ordered by creation date, latest first. Unknown dates (`—`) appear last.

**Branch lifecycle:** Each plan owns a feature branch from Draft→Active through Complete. Branch is `{prefix}/{plan-slug}` (default prefix: `refactor`). Merge to `main` on completion.

---

## Purging Old Completed Plans

When updating `master-plan.md`, **remove any plan that is `✅ Complete` and meets this criteria:**

1. **Completed more than 4 weeks ago** — use completion date from the plan file's Implementation Status

Rationale: Once a plan has been complete for a significant period, its status in the index no longer provides active value. The plan file itself (in `project/plans/`) remains as historical documentation. Only the index entry in `master-plan.md` is removed.

**What to remove:**
- The row from the **Master Progress Table**
- Any overview section for that plan

**What to keep:**
- The plan file in `project/plans/` — it serves as historical documentation
- **Do not renumber** — leave gaps in numbering to preserve historical references

**What NOT to remove:**
- Plans marked `🟡 In Progress`, `⬜ Planned`, or `❌ Blocked` — regardless of age
- Plans marked as superseded — they provide historical context for why a different approach was taken
- Plans that are `✅ Complete` but completed within the last 4 weeks
- Plans with active dependencies (other plans that reference this one as a prerequisite)

---

## Master Progress Table

| # | Plan | Status | Created |
|---|------|--------|---------|
| 12 | [Regex Filter Observability](regex-filter-observability.md) | ⬜ Planned | 2026-05-11 |
| 11 | [Remove scanner.url Config](remove-scanner-url-config.md) | ⬜ Planned | 2026-05-11 |
| 10 | [Improve Agent Domain Design](improve-agent-domain-design.md) | ⬜ Planned | 2026-05-11 |
| 9 | [Agent Observer UseCase](agent-observer-usecase.md) | ✅ Complete | 2026-05-08 |
| 8 | [Scanner Status & Observability Refactor](scanner-status-observability-refactor.md) | ✅ Complete | 2026-05-08 |
| 1 | [Scanner Metrics Refactor](scanner-metrics-refactor.md) | ⬜ Planned | 2026-05-07 |
| 2 | [Scanner View Regression Fix](scanner-view-regression-fix.md) | ⬜ Planned | 2026-05-07 |
| 3 | [Scanner Status Rework](scanner-status-rework.md) | ⬜ Planned (superseded by scanner-status-observability-refactor) | 2026-04-29 |
| 4 | [Design Principles Update](design-principles-update.md) | 🟡 In Progress | 2026-04-28 |
| 5 | [Scanner Event Refactor](scanner-event-refactor.md) | ⬜ Planned (superseded by scanner-status-observability-refactor) | — |
| 6 | [Potential Features](potential-features.md) | ⬜ Planned (backlog) | — |
| 7 | [Scanner Refactor](scanner-refactor.md) | ⬜ Planned | — |

---

## Plan Overviews

### 12. Regex Filter Observability

**Status:** ⬜ Planned
**Created:** 2026-05-11
**Goal:** Track files dropped by agent regex filters — per-agent count in grid, last-10 entries in detail dialog — using the existing `AgentObserverUseCase` pattern.

Full details in [regex-filter-observability.md](regex-filter-observability.md).

### 11. Remove scanner.url Config

**Status:** ⬜ Planned
**Created:** 2026-05-11
**Goal:** Delete dead `scanner.url` config, `FileSystemScannerConfig` class, and all references to it.

Full details in [remove-scanner-url-config.md](remove-scanner-url-config.md).

### 10. Improve Agent Domain Design

**Status:** ⬜ Planned
**Created:** 2026-05-11
**Goal:** Replace magic-string fields in `AgentDefinition` with typed value objects (`AgentType`, `AgentSource`), add constructor validation, fix `FilterResult` mutation bug, and introduce `AttributeConverter` for type-safe persistence.

Full details in [improve-agent-domain-design.md](improve-agent-domain-design.md).

### 8. Scanner Status & Observability Refactor

**Status:** ✅ Complete
**Created:** 2026-05-08
**Completed:** 2026-05-08
**Goal:** Split `ScannerObserverService` into pure metrics + event bus via `ScannerObservabilityUseCase`; introduce `ScannerFileResult` domain enum; move display timers to UI layer.

Full details in [scanner-status-observability-refactor.md](scanner-status-observability-refactor.md).
Knowledge extracted to [dpr-scanner-observability.md](../docs/dpr-scanner-observability.md) and [dpr-scanner-concept.md](../docs/dpr-scanner-concept.md).
Branch `refactor/scanner-observability` merged to `main`.

### 4. Design Principles Update

**Status:** 🟡 In Progress
**Created:** 2026-04-28
**Goal:** ADR cleanup and DPR migration across the project documentation.

Full details in [design-principles-update.md](design-principles-update.md).

---

## Status Symbols

| Symbol | Meaning | When to Use |
|--------|---------|-------------|
| `✅ Complete` | Fully implemented and verified | All deliverables done, code matches plan, branch merged to main |
| `🟡 In Progress` | Actively being worked on | Partial implementation, phases in progress, on feature branch |
| `⬜ Planned` | Defined but not started | Plan written, work not yet begun, no branch yet |
| `❌ Blocked` | Cannot proceed | Blocked by dependency, decision, or issue |

---

## Best Practices

1. **One source of truth** — `master-plan.md` is the single index. Never maintain a separate status list.
2. **Filename-only links** — Since all plans are in the same directory, links are always just the filename (e.g. `[My Plan](my-plan.md)`).
3. **Progressive numbering** — Don't renumber when removing plans. Gaps preserve history and avoid broken references.
4. **Verify before marking complete** — Read the actual code, don't just trust the plan's own status claim.
5. **Update dates** — Always update `Last Updated` in master-plan.md when making changes.
6. **Keep goals concise** — The overview goal should be one line. Details belong in the plan file.
7. **Branch per plan** — Each active plan owns a feature branch (`refactor/<slug>`). Merge to `main` on completion.
8. **Superseded plans stay** — They provide historical context for why a different approach was taken. Never purge superseded entries.
9. **Auto-purge keeps the file** — Only the index entry is removed after 4 weeks. Git preserves everything.
10. **Completion date matters** — Always add a completion date to the plan file when marking complete. This drives the auto-purge timer.

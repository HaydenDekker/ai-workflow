---
name: project-management
description: "Create and maintain project plans, ADRs, DPRs, and design documents. Plans drive agents through incremental change. Plans own feature branches — Draft→Active creates a branch, Active→Complete merges to main. Finished plans are auto-purged from the master index after 4 weeks (git preserves them) while lasting knowledge is extracted to design documents. USE FOR: create plan, update plan, complete plan, archive plan, create ADR, create DPR, create design document, document architecture, document design, project documentation, ADR, architecture decision record, DPR, design pattern record, master plan, document status, extract knowledge from plan, merge plan branch, update master plan, purge old plans. DO NOT USE FOR: writing code, running tests, building, deploying."
license: MIT
metadata:
  author: AI Workflow Team
  version: "2.0.0"
  compatibility: "Git initialized, single-repo project"
---

# Project Management Skill

This skill governs how plans, ADRs, DPRs, and design documents are created, maintained, and retired. These documents are the **memory and navigation system** for agents working on a project.

---

## Core Philosophy

### Code First — Code Tells the Truth

**Documents are reflections of the codebase, not the other way around.** When code and documentation disagree, the code wins. Always read the code before writing or updating a document. Plans, ADRs, and DPRs should describe what the code actually does — they are derived artifacts, not specifications.

This means:
- **Read the code first** before creating a plan or document. The code tells the real story.
- **Update documents from the code**, never from assumptions or memory.
- **When in doubt, trace through the source.** Follow the call chain, check the tests, verify the types.

### Plans Are the Entry Point

Plans are the primary unit of work. They define **what to build** and **in what order**. An agent reads a plan, executes phases incrementally, and updates the plan as it learns. Plans are living documents — they change as implementation reveals new constraints, edge cases, and opportunities.

**Why plans exist:** The context window is limited. An agent cannot hold the full project in memory. A plan with clear, incremental steps is the navigation system that keeps work coherent across sessions. **The first step in any development task is to identify or create the plan being implemented.** Without a plan, work is directionless and context is lost between turns.

### Branches Belong to Plans

Every plan that involves code changes owns a feature branch for its entire lifecycle:

| Stage | Branch State |
|-------|-------------|
| **Draft** | No branch needed. Plan file lives on `main`. |
| **Active** | Branch created: `{prefix}/{plan-slug}` (e.g. `refactor/scanner-observability`). Agent switches to it. |
| **Complete** | Branch merged to `main`, branch deleted. Agent switches to `main`. |
| **(auto-purge)** | Plan entry removed from master plan index after 4 weeks. Plan file stays in git history. |

**Branch naming convention**: `{prefix}/{plan-slug}` where:
- `prefix` comes from `ORCH_BRANCH_PREFIX` env var (default: `refactor`)
- `plan-slug` is the plan filename without `.md` extension

**When to skip branching**: Documentation-only changes, single-commit tasks that won't conflict, or plans explicitly marked as no-branch. In these cases, work directly on `main`.

**Who owns the merge**: The **project-management skill** owns the merge step. The plan-orchestrator executes phases and commits but does not merge. The merge happens as part of plan completion, before archiving.

### Trial-and-Error Is Cheap

Traditional design documents are written before implementation. Here, **the implementation is the first draft of the design**. You start a plan with intent and rough structure, build iteratively, and the final design document emerges from what actually works. The design document presents the **result**, not the prophecy.

### Core Design Principles

Agents **must always apply** these principles to planning, designing, and refactoring. They are non-negotiable constraints on how work is structured:

| Principle | What it means | What to alert on |
|-----------|---------------|-------------------|
| **Hexagonal Architecture** | Domain → Application (use cases, ports) → Adapters (inbound/outbound). The core never depends on the edges. | Adapters depending on each other, domain importing framework classes, controllers calling repositories directly |
| **Domain-Driven Design** | The domain layer owns the business logic. Entities, value objects, and domain events live here. Use case orchestration lives in the application layer. | Business logic leaking into controllers or repositories, anemic domain models |
| **Test-Driven Development** | Tests drive the code. Write the failing test first, then implement the minimum code to pass. Plans are centered around test gaps. | New code without tests, plans that skip test phases, production code written before test scaffolding |

When a plan, implementation, or refactor **diverges from these principles, the agent must identify and alert to the diversion** — either in the plan notes or as a callout in the phase description.

### Historical Memory

Plans, ADRs, and DPRs together form the historical memory of a project:

- **Plans** — the journey (origin → destination, including detours)
- **ADRs** — the decisions (why we chose X over Y)
- **DPRs** — the knowledge (how things work now)
- **Design Documents** — the result (what was built, what it does, how to configure it)
- **Master Plan** — the index (what plans exist, what is their status)

---

## Document Types

| Type | Location | Question | Lifespan | Mutable? |
|------|----------|----------|----------|----------|
| **Plan** | `project/plans/` | What are we building and in what order? | File permanent (git), index entry removed after 4 weeks | Yes — evolves during work |
| **ADR** | `project/adrs/` | Why did we make this decision? | Permanent — stable unless decision reverses | Rarely — append status changes only |
| **DPR** | `project/docs/` | How does this work? | Permanent — stable unless implementation changes | Yes — grows as system grows |
| **Design Document** | `project/docs/` | What was produced, what does it do, how is it configured? | Permanent — the canonical reference | Yes — progressive updates |
| **Master Plan** | `project/plans/master-plan.md` | What plans exist and what is their status? | Permanent — updated as plans are created/completed | Yes — auto-purge + manual updates |

---

## The Plan Lifecycle

A plan moves through four stages:

```
Draft → Active → Complete → (auto-purge from index after 4 weeks)
```

### Stage 1: Draft

A **draft plan** captures intent. It is written before or during the start of work. It contains:

- The problem/goal
- A rough target structure or design
- Phases or milestones (if multi-step)
- Open questions or risks

A draft plan lives on `main` with no feature branch.

**Create a draft plan when:**

- You are about to start meaningful work (beyond a single commit)
- The work spans multiple files, classes, or concepts
- You want to track progress and maintain context across sessions
- The work involves trade-offs or alternatives to explore

**Do NOT create a plan when:**

- The change is a single file edit with clear intent
- The work is purely mechanical (renaming, formatting, boilerplate)
- The change is a bug fix with an obvious solution

**Creating a draft plan:**

1. Write the plan file: `project/plans/<slug>.md`
2. Add a row to the Master Progress Table in `master-plan.md`
3. Commit: `docs: add plan <slug>`

### Stage 2: Active

An **active plan** is being worked on. When a plan transitions from Draft to Active:

1. **Create and switch to feature branch**:
   ```bash
   git checkout -b {prefix}/{plan-slug}
   ```
   Where `prefix` defaults to `refactor` (or from `ORCH_BRANCH_PREFIX` env var).
2. Begin executing phases
3. Mark phases with `✅`, `🔄`, or `⬜`
4. Write notes about what actually happened (not what you planned)
5. Record surprises: unexpected dependencies, edge cases discovered, decisions made mid-stream

**The plan is a logbook, not a contract.** The value is in the record of how you arrived at the destination, not in how faithfully you followed the original route.

**Updating the master plan during work:**

When transitioning from Draft to Active, update the plan's status in the master plan table to `🟡 In Progress`.

### Stage 3: Complete

A **complete plan** has all phases done, tests passing, and code verified. The completion process:

1. **Extract lasting knowledge** (see table below)
2. **Merge branch to `main`**:
   ```bash
   git checkout main
   git pull origin main
   git merge {prefix}/{plan-slug} --no-edit
   git branch -d {prefix}/{plan-slug}
   ```
   Handle merge conflicts if any. If conflicts are severe, consider a rebase or manual resolution.
3. **Update the master plan**:
   - Change plan status in the table to `✅ Complete`
   - Add a completion date note to the plan file's Implementation Status section
   - Optionally add a completion overview section in the master plan
4. **Commit**: `docs: complete plan <slug>, merge to main`

**Knowledge extraction before merging:**

| Knowledge Type | Extract To | Example |
|----------------|------------|---------|
| Environment variables / configuration properties | Design document or DPR | `application.yml` properties, `@ConfigurationProperties` classes |
| Architecture decisions made during implementation | ADR | Why hexagonal over layered, why SQLite over H2 |
| How the feature works | DPR or design document | Scanner lifecycle, agent-subscriber model |
| Use cases and functional behaviour | Design document | "Agent can be created, edited, deleted" |
| API contracts, DTOs, port interfaces | Design document or ADR | `AgentRepository` port, `AgentInfo` DTO |
| Test strategy / patterns | DPR | How integration tests are structured |
| Gotchas / lessons learned | Design document (notes section) | "Mono<Void> never invokes Consumer" |

**Superseding a plan:**

When a new plan supersedes an older one (e.g., a better approach is found):

1. Mark the old plan's status as `⬜ Planned (superseded by <new-plan-slug>)`
2. **Do NOT delete the old plan file** — it provides historical context
3. **Do NOT create a branch for the superseded plan** if it was never started
4. Superseded plans are **exempt from auto-purge** — they stay in the index indefinitely as historical context

### Stage 4: Auto-Purge (From Index)

After a plan has been `✅ Complete` for more than **4 weeks**, its entry is automatically removed from the master plan index during the next status update.

**What is purged:**
- The row from the **Master Progress Table**
- The overview section (if one exists in the master plan)

**What is kept:**
- The plan file in `project/plans/` — it serves as historical documentation
- The git history of all changes
- Any ADRs, DPRs, or design documents extracted during completion

**What is NOT purged:**
- Plans marked `🟡 In Progress`, `⬜ Planned`, or `❌ Blocked` — regardless of age
- Plans marked as superseded — they stay as historical context
- Plans that are `✅ Complete` but completed within the last 4 weeks
- Plans with active dependencies (other plans that reference this one as a prerequisite)

**Purge execution:**

When updating the master plan (any status update), check all `✅ Complete` entries:
- If the plan was completed more than 4 weeks ago → remove its table row and overview
- If the plan has no completion date → leave it (unknown age)
- Update the `Last Updated` date in the master plan

---

## Creating a Plan

### File Naming

```
project/plans/<slug>.md
```

Use kebab-case slugs: `hex-arch-refactor.md`, `scanner-observer-usecase.md`, `add-sqldb.md`.

### Single UseCase Focus

**A plan should focus on a single usecase.** Software exists to be used — each plan targets one actor goal: "user can do X," "system does Y when Z happens." Multi-use-case plans fracture across context windows and lose coherence. If the work touches multiple usecases, split into multiple plans or create a parent plan that coordinates child plans.

### Tests Are The Plan's Spine

**The most important parts of a plan are the test sources.** Software behaviour is defined by its tests. A plan that does not identify the tests it depends on is incomplete.

When creating a plan:

1. **Identify existing tests first** — what tests already cover the affected code? Which tests define the current (possibly broken) behaviour?
2. **Identify test gaps** — what behaviour needs tests that don't exist yet? What edge cases are uncovered?
3. **Center the plan around making tests pass** — each phase should produce green tests before moving to the next. If a phase has no test to validate it, reconsider whether the phase is necessary or whether the test belongs in a different layer.

The plan's phase structure naturally follows TDD:

```
Phase 1: Write failing test(s) for the new/changed behaviour
Phase 2: Implement minimum code to pass the test(s)
Phase 3: Refactor (if needed) while keeping tests green
Phase 4: Add integration/E2E tests for the full flow
```

### Hexagonal Structure

Plans must follow hexagonal architecture. When describing what to build, always identify:

- **Port** (interface in application layer) — what the use case needs
- **Use case / service** (application layer) — the business logic orchestration
- **Domain** — entities, value objects, domain events
- **Inbound adapter** — controller, UI view, event listener (how the outside world calls in)
- **Outbound adapter** — repository, file watcher, HTTP client (how the use case calls out)

This structure keeps the plan aligned with the architecture from day one.

### Minimum Structure

```markdown
# Plan: <Title>

## Problem
What is wrong or missing? Why does this work need to happen?

## Target
What will exist or behave differently when this is done?

## Implementation Status: ⬜ Draft | 🔄 Active | ✅ Complete

## Existing Tests
| Test Class | What it covers | Status |
|------------|---------------|--------|
| `FooServiceTest` | Creation flow | ✅ Green — verifies current behaviour |
| `BarControllerTest` | GET endpoint | ⚠️ Thin — only checks annotations |

## Test Gaps
- No test for X scenario
- No integration test for Y flow
- E2E coverage missing for Z view

## Phases

### Phase 0: <Name>
- [ ] Task A
- [ ] Task B

### Phase 1: <Name>
- [ ] Task C
- [ ] Task D

## Notes
[Any observations, deviations, or lessons learned during implementation]
```

### Progressive Updates

As you work through phases, update the plan:

```markdown
## Implementation Status: 🔄 Active

## Phases

### Phase 0: <Name> ✅ Done
- [x] Task A — done, required additional import for X
- [x] Task B — done, simpler than expected

### Phase 1: <Name> 🔄 In Progress
- [ ] Task C — blocked on Y
- [ ] Task D
```

When complete:

```markdown
## Implementation Status: ✅ Complete (2026-05-08)

## Notes
- Phase 2 required an additional step (Phase 2.5) to handle Z
- Full test suite passes (287 tests, 0 failures)
- Extracted configuration details to dpr-<slug>.md
```

---

## Maintaining the Master Plan

The master plan index (`project/plans/master-plan.md`) is the single source of truth for plan status.

### Master Plan Structure

```markdown
# Master Plan — Project Index

> **Last Updated:** YYYY-MM-DD

## How to Add a Plan

Each plan file must contain a title (e.g. `# Plan: Plan Name`).

To add it to this index:

1. Add a row to the Master Progress Table: number, link, status, date.
2. Optionally add an overview section below with **goal** only. The goal states **what** the plan covers — not why or how. Full details belong in the plan file.

**Ordering:** Plans are ordered by creation date, latest first, within the table. Unknown dates (`—`) appear last.

---

## Purging Old Completed Plans

When updating `master-plan.md`, **remove any plan that is `✅ Complete` and meets this criteria:**

1. **Completed more than 4 weeks ago** — use completion date from the plan file's Implementation Status

Rationale: Once a plan has been complete for a significant period, its status in the index no longer provides active value. The plan file itself (in `project/plans/`) remains as historical documentation. Only the index entry is removed.

**What to remove:**
- The row from the **Master Progress Table**
- Any overview section for that plan

**What to keep:**
- The plan file in `project/plans/` — historical documentation
- **Do not renumber** — leave gaps in numbering to preserve historical references

**What NOT to remove:**
- Plans marked `🟡 In Progress`, `⬜ Planned`, or `❌ Blocked` — regardless of age
- Plans marked as superseded — they provide historical context
- Plans that are `✅ Complete` but completed within the last 4 weeks
- Plans with active dependencies (other plans that reference this one)

---

## Master Progress Table

| # | Plan | Status | Created |
|---|------|--------|---------|
| — | _No plans yet. Add plans using the project-management skill._ | | |

---

## Plan Overviews

_Overview sections for active and recently completed plans go here._

### 1. Plan Name

**Status:** 🟡 In Progress
**Created:** YYYY-MM-DD
**Goal:** Brief one-line description of what this plan achieves.

Full details in [plan-file.md](plan-file.md).
```

### Table Formatting

The table always has columns: `#`, `Plan`, `Status`, `Created`

- **Status values** use the symbols from the legend below
- Use `—` for unknown creation dates
- Sort by creation date (latest first)
- Plans with unknown dates appear last
- If all plans have been purged, show the placeholder row
- **Progressive numbering** — don't renumber when removing plans. Gaps preserve history.

### Updating the Master Plan

| Event | Action |
|-------|--------|
| New plan created | Add row to table with `⬜ Planned` status and creation date |
| Plan transitions to Active | Update status to `🟡 In Progress` |
| Plan completed | Update status to `✅ Complete`, add completion date to plan file, optionally add overview |
| Plan branch merged | (Already part of completion — merge happens before status update) |
| Status update (any reason) | Check all `✅ Complete` entries for auto-purge (> 4 weeks), update `Last Updated` date |
| Plan superseded | Mark old plan as `⬜ Planned (superseded by <slug>)` — do NOT purge superseded plans |

### Status Symbols

| Symbol | Meaning | When to Use |
|--------|---------|-------------|
| `✅ Complete` | Fully implemented and verified | All deliverables done, code matches plan, branch merged to main |
| `🟡 In Progress` | Actively being worked on | Partial implementation, phases in progress, on feature branch |
| `⬜ Planned` | Defined but not started | Plan written, work not yet begun, no branch yet |
| `❌ Blocked` | Cannot proceed | Blocked by dependency, decision, or issue |

### Best Practices

1. **One source of truth** — `master-plan.md` is the single index. Never maintain a separate status list.
2. **Filename-only links** — Since all plans are in the same directory, links are always just the filename (e.g. `[My Plan](my-plan.md)`).
3. **Progressive numbering** — Don't renumber when removing plans. Gaps preserve history and avoid broken references.
4. **Verify before marking complete** — Read the actual code, don't just trust the plan's own status claim.
5. **Update dates** — Always update `Last Updated` in master-plan.md when making changes.
6. **Keep goals concise** — The overview goal should be one line. Details belong in the plan file.
7. **Branch per plan** — Each active plan owns a feature branch. Merge to `main` on completion.
8. **Superseded plans stay** — They provide historical context for why a different approach was taken.
9. **Auto-purge keeps the file** — Only the index entry is removed. Git preserves everything.
10. **Completion date matters** — Always add a completion date to the plan file when marking complete. This drives the auto-purge timer.

---

## Creating an ADR

### When

Create an ADR when a decision affects **how the application communicates with the outside world** or adopts a **new architectural pattern**.

### File Naming

```
project/adrs/adr-NNN-<slug>.md
```

Number sequentially. Check existing ADRs for the next available number.

### Structure

```markdown
# ADR-NNN: <Title>

**Date**: YYYY-MM-DD
**Status**: Accepted | Superseded | Deprecated

## Context
Problem space, constraints, alternatives considered.

## Decision
What was decided and how.

### Architecture Overview
ASCII diagram showing adapter placement, data flow, responsibility boundaries.

### Component Structure
Package / file layout.

### Configuration
`application.yml` properties, `@ConfigurationProperties` beans.

### Dependencies
Maven coordinates, versions.

### Testing Strategy
How the adapter is tested.

## Consequences
What follows from this decision (positive and negative).

## References
Related ADRs, DPRs, external docs.
```

---

## Creating a DPR

### When

Create a DPR when you need to explain **how something works** — implementation details, code examples, tutorials, recurring patterns.

### File Naming

```
project/docs/dpr-<slug>.md
```

### Structure

```markdown
# DPR: <Title>

## Overview
Brief summary of what the concept is and how it works.

## How It Works
Detailed explanation with diagrams, code examples, walkthroughs.

## Key Components
Classes, interfaces, configuration involved.

## Configuration
Properties, environment variables, startup requirements.

## Usage Examples
How to use the concept in practice.

## Gotchas
Pitfalls, edge cases, common mistakes.

## Related Documents
Parent ADR, related DPRs, source code paths.
```

---

## Creating a Design Document

### When

A design document is the **extracted knowledge** from a completed plan. It describes:

- **What was produced** — components, classes, modules
- **What it does** — use cases, behaviour
- **How to configure it** — properties, environment variables, dependencies

Create a design document when:

- A plan is complete and contains lasting operational knowledge
- You need a canonical reference for a subsystem
- Onboarding or documentation requires a "what is this and how do I use it" guide

### File Naming

Design documents live in `project/docs/` alongside DPRs. Use the `dpr-` prefix if the document is primarily explanatory (how something works), or a descriptive prefix like `design-` if it is primarily a product description (what was built).

```
project/docs/design-<slug>.md
```

### Structure

```markdown
# Design: <Title>

## What It Is
One-paragraph summary of the feature/subsystem.

## What It Does
Use cases, user goals, actor roles.

## Components
| Component | Location | Role |
|-----------|----------|------|
| ClassName | package/path | Brief description |

## Configuration
### Properties
| Property | Default | Description |
|----------|---------|-------------|

### Environment Variables
| Variable | Required | Description |

## Dependencies
Maven coordinates or npm packages.

## How It Works
High-level flow. Link to DPR for deep implementation details.

## API Contract
Port interfaces, DTOs, REST endpoints.

## Testing
How the feature is tested. Link to test classes.

## Notes
Lessons learned, gotchas, design trade-offs.

## Related Documents
- ADR-NNN (why)
- DPR (how)
- Source code paths
```

---

## Agent Workflow

When an agent starts working on a feature or refactor:

1. **Read the code first** — understand what actually exists before reading or writing any document. The code tells the truth.
2. **Check the master plan** — is there already a plan for this work? If yes, follow it.
3. **If no plan exists** and the work is significant, create one first. The plan identifies existing tests, test gaps, and phases centered around making tests pass.
4. **Read the plan** — understand phases, target state, and open questions.
5. **If transitioning from Draft to Active**: create and switch to feature branch (`git checkout -b {prefix}/{plan-slug}`)
6. **Execute phases incrementally** — compile and test after each phase. Each phase produces green tests before moving on.
7. **Update the plan** — mark phases, add notes, record deviations. Flag any diversion from hexagonal architecture, DDD, or TDD.
8. **When complete** — extract lasting knowledge to ADRs/DPRs/design documents, then merge branch to `main` and delete branch.
9. **Update the master plan** — set status to `✅ Complete`, add completion date, check for auto-purge candidates.
10. **Commit** — `docs: complete plan <slug>, merge to main`

---

## Cross-Reference Rules

- **Plans may reference** ADRs, DPRs, and design documents for context.
- **ADRs and DPRs must NOT reference plans** — plans are transient; ADRs/DPRs are durable.
- **Design documents may reference** ADRs (why) and DPRs (how).
- **The master plan** is the only document that indexes all plans.

---

## Quick Decision Guide

| Situation | Document |
|-----------|----------|
| Starting multi-phase work | Plan |
| Choosing between architectural alternatives | ADR |
| Explaining how a feature works | DPR |
| Describing what was built and how to configure it | Design Document |
| Tracking all plan statuses | Master Plan |
| Work is a single-obvious change | No document needed — commit message is sufficient |

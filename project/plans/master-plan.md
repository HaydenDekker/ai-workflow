# Master Plan — Project Index

Central index of all active, completed, and archived plans for the AI Workflow project.

---

## Active Plans

| Plan | File | Status | Created | Description |
|------|------|--------|---------|-------------|
| Design Principles Update | [design-principles-update.md](design-principles-update.md) | 🔄 In Progress | 2026-04-28 | ADR cleanup and DPR migration; Phase 0 done, remaining phases pending. |
| Scanner Refactor | [scanner-refactor.md](scanner-refactor.md) | ⬜ Draft | — | Make `Scanner` the domain concept; consolidate status, idle detection, error handling, and metrics. |
| Scanner Status Rework | [scanner-status-rework.md](scanner-status-rework.md) | ⬜ Draft | 2026-04-29 | Fix static scanner status lifecycle; introduce idle detection and proper ERROR state transitions. |
| Scanner Event Refactor | [scanner-event-refactor.md](scanner-event-refactor.md) | ⬜ Draft | — | Simplify `ScannerObserverUseCase` to single `recordScannerEvent()` method with combined status + event type. |
| Potential Features | [potential-features.md](potential-features.md) | ⬜ Backlog | — | Feature backlog (dynamic model discovery, etc.) from the Ollama → OpenAI refactor. |

---

## Completed Plans

| Plan | File | Status | Created | Description |
|------|------|--------|---------|-------------|
| Hexagonal Architecture Refactor | [hex-arch-refactor.md](hex-arch-refactor.md) | ✅ Complete | — | Full restructure into domain → application → adapter layers across all 7 phases. |
| Clean Up Scanner-Agent Relation | [clean-up-scanner-agent-relation.md](clean-up-scanner-agent-relation.md) | ✅ Complete | — | Remove backwards FK (`scannerId` on `AgentEntity`); scanner references agent directly. |
| Scanner Observer Use Case | [scanner-observer-usecase.md](scanner-observer-usecase.md) | ✅ Complete | 2026-04-29 | Replace Micrometer scanner metrics with `ScannerObserverUseCase`. |
| Phase 5 Cleanup | [phase-5-cleanup-plan.md](phase-5-cleanup-plan.md) | ✅ Complete | — | Detailed cleanup of old packages after hex-arch refactor (imports, Javadoc, test moves, deletions). |

---

## Archived Plans (Removed from Repository)

Plans that were completed and subsequently removed from the repository.

| Plan | File | Created | Deleted | Description |
|------|------|---------|---------|-------------|
| Agent Terminology Refactor | ~~agent-terminology-refactor.md~~ | 2026-04-13 | 2026-04-13 | Rename infrastructure from "pipeline" to "agent" terminology. |
| LLM Observability | ~~llm-observability.md~~ | 2026-04-13 | 2026-04-19 | Standalone `/observability` route with adapter health checks and SQLite status persistence. |
| Ollama → OpenAI Term Refactor | ~~ollama-term-refactor.md~~ | 2026-04-13 | 2026-04-20 | Replace Ollama-specific terminology with OpenAI API terminology across the codebase. |
| FileSystem Workflow Integration Test Refactor | ~~FileSystemWorkflowIntegrationTest_refactor.md~~ | 2026-02-02 | 2026-04-14 | Simplify integration test to focus on core file system workflow with a single adapter. |
| Integrating Non-Standard Chat Mocks | ~~Integrating non-standard chat mocks into ChatClientMockBuilder.md~~ | 2026-02-02 | 2026-04-14 | Unify all test ChatClient mocks through `ChatClientMockBuilder` pattern. |
| PipelineInfo Refactoring | ~~pipelineinfo-refactoring.md~~ | 2026-02-02 | 2026-04-14 | Refactor `PipelineInfo` to include full `AgentDefinition` as a 1-to-1 relationship. |
| Grid View Implementation | ~~grid-view-implementation.md~~ | 2026-02-02 | 2026-04-14 | Initial Vaadin Flow grid view displaying `PipelineInfo` objects. |
| Fix Integration Test Failures | ~~fix-integration-test-failures.md~~ | 2026-04-14 | 2026-04-20 | Fix 12 failing integration tests caused by external LLM HTTP calls at bean creation time. |
| Add SQLite Database | ~~add-sqldb.md~~ | 2026-04-14 | 2026-04-20 | Add SQLite database configuration for `FileMetadataEntity` with multi-database foundation. |
| LLM Observability UI Component | ~~llm-observability-ui-component.md~~ | 2026-04-19 | 2026-04-20 | Reusable Vaadin components for LLM endpoint health display with auto-refresh. |
| Observability TDD Setup | ~~observabiltiy_tdd.md~~ | 2026-02-02 | 2026-04-20 | TDD-driven observability foundation (metrics, logs, tracing). |
| Agent Definition UI | ~~UI.md~~ | 2026-02-02 | 2026-04-20 | Form-based UI for CRUD of `AgentDefinition` records in `PipelineRestController`. |
| Agent Creation Dialog | ~~agent-creation-dialog-plan.md~~ | 2026-04-21 | 2026-04-28 | Agent creation modal dialog + test view. |
| Agent Persistence | ~~agent-persistence.md~~ | 2026-04-25 | 2026-04-28 | Migrate agent state from in-memory `ConcurrentHashMap` to SQLite via JPA. |
| Agent Scanners | ~~agent-scanners.md~~ | 2026-04-25 | 2026-04-28 | First-class scanner lifecycle management with dynamic registration. |
| Agent Modification | ~~agent-modification.md~~ | 2026-04-26 | 2026-04-28 | Agent detail dialog for editing and deleting agents. |
| Direct Integration FileSystem Adapter | ~~direct-integration-filesystem-adapter.md~~ | 2026-04-27 | 2026-04-28 | Fix `FluxMessageChannel` delivery failure with direct adapter integration. |
| Spring Integration Deprecation | ~~spring-integration-deprecation.md~~ | 2026-04-27 | 2026-04-28 | Migrate from Spring Integration DSL to native Java NIO and Reactor for file watching. |
| Use-Case Refactor | ~~usecase-refactor.md~~ | 2026-04-27 | 2026-04-28 | Update domain layer terminology from "services/managers" to "usecases". |
| Advanced Metrics | ~~advanced_metrics.md~~ | 2026-02-02 | 2026-04-28 | Naming/tagging conventions and metric definitions for pipeline observability. |
| Observability Plan | ~~observability-plan.md~~ | 2026-02-02 | 2026-04-28 | High-level observability goals: visibility, performance, and reliability tracking. |
| Scanners Metrics | ~~scanners_metrics.md~~ | 2026-04-28 | 2026-04-28 | Scanner metrics instrumentation with Micrometer (later replaced by ScannerObserverUseCase). |

---

## Legend

| Marker | Meaning |
|--------|---------|
| ✅ Complete | All phases implemented, verified, and merged (plan file retained) |
| 🔄 In Progress | Work started but not yet finished |
| ⬜ Draft | Design written, implementation not started |
| ⬜ Backlog | Ideas/requirements captured for future consideration |
| ~~strikethrough~~ | Plan file removed from repository after completion |

---

## Status Summary

- **Active:** 5 plans
- **Completed (retained):** 4 plans
- **Archived (removed):** 22 plans

# Plan: Improve Agent Domain Design

> **Created:** 2026-05-11

## Problem

The agent domain model is under-typed and unsafe. `AgentDefinition` is a 7-field `String` record with no validation, no value objects, and no compile-time guarantees. Magic strings (`"Reduction"`, `"YAML"`, `"DYNAMIC"`, `"path"`, `"name"`, `"ext"`) are scattered across adapters, UI components, and domain code. Additionally, `PromptResponse.createOutputFileName()` mutates a shared mutable map inside `FilterResult`, creating a concurrency hazard.

## Target

- `AgentDefinition` uses typed value objects (`AgentType` enum, validated regex) instead of raw strings
- Constructor validation rejects invalid definitions at construction time
- `FilterResult.groups()` is immutable — no shared-state mutation in `createOutputFileName()`
- `AgentSource` enum replaces `"YAML"`/`"DYNAMIC"` magic strings
- `AgentEntity` uses `AttributeConverter` for type-safe JSON round-trip
- All existing tests pass through every phase; no behavioral regressions

## Implementation Status: ✅ Complete (2026-05-12)

> **Branch:** `refactor/improve-agent-domain-design` — merged to main, branch deleted
> **Tests:** 461 tests pass, 0 failures, 2 skipped
> **Commits:** 6 (one per phase)

## Existing Tests

| Test Class | What it covers | Status |
|------------|---------------|--------|
| `RegexInputFileFilterTest` | Regex matching, group extraction (path/name/ext) | ✅ Green — 12 parameterized cases |
| `OutputFilenameTemplateTest` | Template placeholder substitution | ✅ Green — 1 case |
| `MapAgentLLMAdapterTest` | Map adapter produces response | ✅ Green — constructs `AgentDefinition` with all String fields |
| `SplitterLLMAdapterTest` | Split adapter produces multiple responses | ✅ Green — `agentType: "Split"` |
| `LLMReducerAdapterTest` | Reducer adapter concatenates | ✅ Green — `agentType: "Reduction"` |
| `AgentBuilderTest` | Pipeline filter + persist flow | ✅ Green — constructs `AgentDefinition` directly |
| `AgentEntityTest` | Entity field access + JSON serialization | ✅ Green — tests JSON round-trip |
| `AgentLifecycleServiceTest` | CRUD, enable/disable, refresh, YAML init | ✅ Green — 12 tests |
| `AgentLifecycleServicePersistenceTest` | DB persistence | ✅ Green |
| `AgentLifecycleServiceScannerRestoreTest` | Scanner restore from DB | ✅ Green |
| `AgentConfiguratorTest` | Pipeline configuration | ✅ Green |
| `AgentConfiguratorObserverTest` | Observer hooks | ✅ Green |
| `AgentControllerTest` | REST endpoints | ✅ Green |
| `AgentCreationDialogTest` | UI creation dialog | ✅ Green |
| `AgentDetailDialogTest` (implied) | UI detail dialog | ✅ Green |
| `EndToEndTestHarnessTest` | Full pipeline e2e | ✅ Green |
| `AgentPipelineTest` | Full pipeline flow | ✅ Green |

## Test Gaps

- No test for `PromptResponse.createOutputFileName()` directly (tested indirectly through `AgentBuilderTest`)
- No test for `AgentDefinition` constructor validation (none exists — record has no validation)
- No test for `AgentInfo` construction/fields directly
- No test verifying `FilterResult.groups()` immutability (it's mutable today — the bug)
- `AgentLifecycleServiceTest` constructs `AgentDefinition` with raw strings — no test for typed alternatives
- No test for invalid regex in `AgentDefinition` construction
- UI dialog tests use `String[] AGENT_TYPES` — no domain-level enum backing them

## Phases

### Phase 0: Introduce `AgentType` enum and wire `LLMAdapterFactory` ✅ Done

- [x] 0.1 Created `AgentType` enum with `MAP`, `REDUCTION`, `SPLIT` values and `fromString()` factory
- [x] 0.2 Created `AgentType` enum in `domain/agent/AgentType.java`
- [x] 0.3 Added `agentType` field to `AgentDefinition` as `AgentType`
- [x] 0.4 Updated `LLMAdapterFactory.create()` to switch on `AgentType` enum
- [x] 0.5 Updated `TestData.basicPrompt()` to use `AgentType.MAP`
- [x] 0.6 Updated all test classes that construct `AgentDefinition` with raw string `agentType`
- [x] 0.7 Updated YAML workflow files (`"Reduce"` → `"Reduction"`)
- [x] 0.8 Updated `AgentCreationDialog.AGENT_TYPES` and `AgentDetailDialog.AGENT_TYPES` to derive from `AgentType` enum
- [x] 0.9 Verified `./mvnw test -q` green

**Notes:** YAML deserialization required `@JsonCreator` factory methods on the enum. The `"Reduce"` vs `"Reduction"` inconsistency was found and fixed in YAML files during implementation.

### Phase 1: Add constructor validation to `AgentDefinition` ✅ Done

- [x] 1.1 Created `AgentDefinitionTest` — verifies constructor rejects null `title`, null `body`, null `fileInputRegex`; invalid regex throws `IllegalArgumentException`
- [x] 1.2 Added constructor validation: `Objects.requireNonNull` + `Pattern.compile()` on regex
- [x] 1.3 Updated `TestData.basicPrompt()` and all test fixtures to pass valid data
- [x] 1.4 Updated `AgentCreationDialog` and `AgentDetailDialog` to validate regex before constructing definition
- [x] 1.5 Verified `./mvnw test -q` green

**Notes:** Low risk as expected. All existing code already passed non-null strings — only invalid inputs would fail.

### Phase 2: Fix `createOutputFileName()` mutation + immutable `FilterResult` ✅ Done

- [x] 2.1 Created `PromptResponseTest` — verifies `createOutputFileName()` is idempotent and thread-safe
- [x] 2.2 Changed `FilterResult.groups()` to return `Map.ofEntries(...)` — immutable map
- [x] 2.3 Fixed `PromptResponse.createOutputFileName()` to merge groups into a new map instead of mutating the existing one
- [x] 2.4 Created `FilterResultTest` — verifies groups map is immutable (attempt to put throws `UnsupportedOperationException`)
- [x] 2.5 Verified `./mvnw test -q` green

**Notes:** The shared-mutable-state bug was confirmed. Making `groups()` immutable exposed the only mutation site. No concurrency issues found in practice — the bug was latent.

### Phase 3: Introduce `AgentSource` enum ✅ Done

- [x] 3.1 Created `AgentSourceTest` — verifies enum values (`YAML`, `DYNAMIC`) and `fromString()` parsing
- [x] 3.2 Created `AgentSource` enum in `domain/agent/AgentSource.java`
- [x] 3.3 Replaced `source` field in `AgentInfo` from `String` to `AgentSource`
- [x] 3.4 Replaced `source` field in `AgentEntity` from `String` to `AgentSource` with `@Enumerated(EnumType.STRING)`
- [x] 3.5 Updated `AgentRepository` port `save()` signature: `void save(String id, AgentDefinition definition, AgentSource source)`
- [x] 3.6 Updated `AgentRepositoryAdapter` and `AgentJpaRepository`
- [x] 3.7 Updated `AgentLifecycleService` — all `"YAML"` and `"DYNAMIC"` string literals → enum values
- [x] 3.8 Updated `AgentInfoDTO` to use `AgentSource`
- [x] 3.9 Verified `AgentController` REST serialization (enum serializes to string correctly)
- [x] 3.10 Updated UI components that display `source`
- [x] 3.11 Updated all test classes referencing source strings
- [x] 3.12 Verified `./mvnw test -q` green

**Notes:** `EnumType.STRING` handled existing DB rows transparently — no migration needed. Medium risk confirmed but manageable.

### Phase 4: `AttributeConverter` for `AgentEntity` JSON round-trip ✅ Done

- [x] 4.1 Created `AgentDefinitionConverterTest` — verifies `AttributeConverter<AgentDefinition, String>` serializes/deserializes correctly with all fields including `AgentType` enum
- [x] 4.2 Created `AgentDefinitionConverter` class implementing `AttributeConverter<AgentDefinition, String>`
- [x] 4.3 Added `@Convert(converter = AgentDefinitionConverter.class)` to `AgentEntity.agentDefinitionJson`
- [x] 4.4 Updated `AgentRepositoryAdapter` to work with the converter (no manual Jackson calls needed)
- [x] 4.5 Updated `AgentEntityTest` to verify JSON round-trip through converter
- [x] 4.6 Verified `./mvnw test -q` green

**Notes:** No schema change — the converter encapsulates the same Jackson logic. Low risk confirmed.

### Phase 5: Clean up `PromptTriggerEvent` and eliminate `AgentInfoDTO` redundancy ✅ Done

- [x] 5.1 Audited `PromptTriggerEvent` usage — verified all consumers
- [x] 5.2 Removed `PromptTriggerEvent` entirely (no longer needed — agent type is now `AgentType` enum)
- [x] 5.3 Evaluated `AgentInfoDTO` vs `AgentInfo` — removed `AgentInfoDTO`, return `AgentInfo` directly from REST controller
- [x] 5.4 Updated `AgentController` and all callers
- [x] 5.5 Verified `./mvnw test -q` green

**Notes:** `PromptTriggerEvent` was indeed internal and single-consumer — removal was safe. `AgentInfoDTO` was a pure pass-through record with no transformation — eliminated it cleanly.

## Architecture Boundaries

```
domain/agent/                          ← NEW: AgentType, AgentSource
    AgentDefinition (updated)          ← NEW: typed fields, constructor validation
    AgentInfo (updated)                ← AgentSource instead of String
    PromptTriggerEvent → PromptTriggerSource  ← renamed

domain/shared/
    RegexInputFileFilter.FilterResult  ← immutable groups()

domain/prompt/
    PromptResponse                     ← fixed createOutputFileName()

application/agent/port/
    AgentRepository                    ← AgentSource in save()

adapter/outbound/persistence/agent/
    AgentEntity                        ← AgentSource enum field, @Convert
    AgentDefinitionConverter           ← NEW: AttributeConverter
    AgentRepositoryAdapter             ← updated for converter

adapter/outbound/llm/
    LLMAdapterFactory                  ← switch on AgentType enum

adapter/inbound/rest/dto/
    AgentInfoDTO → removed (use AgentInfo directly)

adapter/inbound/ui/component/
    AgentCreationDialog                ← AGENT_TYPES from AgentType enum
    AgentDetailDialog                  ← AGENT_TYPES from AgentType enum
```

## Notes

- Each phase is independently compilable and testable
- Phase 0 and Phase 3 are structurally identical (String → enum) — same pattern, different fields
- Phase 2 fixes an actual concurrency bug — prioritize if any multi-threaded use is suspected
- YAML deserialization of enums will need `@JsonCreator` factory methods on each enum
- The `"Reduce"` vs `"Reduction"` inconsistency in YAML files (function-analysis uses `"Reduce"`, factory checks `"Reduction"`) is a latent bug revealed by Phase 0

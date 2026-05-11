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

## Implementation Status: ⬜ Draft

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

### Phase 0: Introduce `AgentType` enum and wire `LLMAdapterFactory`

**Goal:** Replace magic-string agent type (`"Map"`, `"Reduction"`, `"Split"`) with a domain enum.

- [ ] 0.1 Write test: `AgentTypeTest` — verify enum values (`MAP`, `REDUCTION`, `SPLIT`), `fromString()` parsing handles existing YAML values (`"Map"`, `"Reduction"`, `"Split"`, `null` → `MAP`), and unknown values fail gracefully
- [ ] 0.2 Create `AgentType` enum in `domain/agent/AgentType.java` with `fromString(String)` factory
- [ ] 0.3 Add `agentType` field to `AgentDefinition` as `AgentType` (with `@JsonAlias` for YAML compat if needed)
- [ ] 0.4 Update `LLMAdapterFactory.create()` to switch on `AgentType` enum (no more `if "Reduction".equals(...)`)
- [ ] 0.5 Update `TestData.basicPrompt()` to use `AgentType.MAP`
- [ ] 0.6 Update all test classes that construct `AgentDefinition` with raw string `agentType`
- [ ] 0.7 Update YAML workflow files if they use inconsistent values (`"Reduce"` → `"Reduction"`)
- [ ] 0.8 Update `AgentCreationDialog.AGENT_TYPES` and `AgentDetailDialog.AGENT_TYPES` to derive from `AgentType` enum
- [ ] 0.9 Run `./mvnw test -q` and verify green

**Risk:** YAML deserialization of `agentType` field. YAML files currently use `"Map"`, `"Reduction"`, `"Split"` — must ensure Jackson/SnakeYaml handles the enum mapping. May need `@JsonCreator` on enum.

### Phase 1: Add constructor validation to `AgentDefinition`

**Goal:** Reject invalid agent definitions at construction time.

- [ ] 1.1 Write test: `AgentDefinitionTest` — verify constructor rejects null `title`, null `body`, null `fileInputRegex`; verify invalid regex throws `IllegalArgumentException`; verify valid definitions construct successfully
- [ ] 1.2 Add canonical constructor validation to `AgentDefinition` (`Objects.requireNonNull` + `Pattern.compile()` on regex)
- [ ] 1.3 Update `TestData.basicPrompt()` and all test fixtures to pass valid data
- [ ] 1.4 Update `AgentCreationDialog` and `AgentDetailDialog` to validate regex before constructing definition
- [ ] 1.5 Run `./mvnw test -q` and verify green

**Risk:** Low. Constructor validation only rejects clearly invalid inputs. All existing code already passes non-null strings.

### Phase 2: Fix `createOutputFileName()` mutation + immutable `FilterResult`

**Goal:** Eliminate the shared-mutable-state bug in `PromptResponse.createOutputFileName()` and make `FilterResult.groups()` immutable.

- [ ] 2.1 Write test: `PromptResponseTest` — verify `createOutputFileName()` is idempotent (calling twice returns same result), and thread-safe (concurrent calls don't corrupt each other)
- [ ] 2.2 Change `FilterResult.groups()` to return `Map.ofEntries(...)` or `Collections.unmodifiableMap(...)` — immutable map
- [ ] 2.3 Fix `PromptResponse.createOutputFileName()` to merge groups into a new map instead of mutating the existing one
- [ ] 2.4 Write test: `FilterResultTest` — verify groups map is immutable (attempt to put throws `UnsupportedOperationException`)
- [ ] 2.5 Run `./mvnw test -q` and verify green

**Risk:** Low. The mutation in `createOutputFileName()` is the only writer to the groups map. Making it immutable exposes this as the only change needed.

### Phase 3: Introduce `AgentSource` enum

**Goal:** Replace `"YAML"`/`"DYNAMIC"` magic strings with a typed enum.

- [ ] 3.1 Write test: `AgentSourceTest` — verify enum values (`YAML`, `DYNAMIC`), `fromString()` parsing
- [ ] 3.2 Create `AgentSource` enum in `domain/agent/AgentSource.java`
- [ ] 3.3 Replace `source` field in `AgentInfo` from `String` to `AgentSource`
- [ ] 3.4 Replace `source` field in `AgentEntity` from `String` to `AgentSource`
- [ ] 3.5 Update `AgentRepository` port `save()` signature: `void save(String id, AgentDefinition definition, AgentSource source)`
- [ ] 3.6 Update `AgentRepositoryAdapter` and `AgentJpaRepository`
- [ ] 3.7 Update `AgentLifecycleService` — all `"YAML"` and `"DYNAMIC"` string literals → enum values
- [ ] 3.8 Update `AgentInfoDTO` to use `AgentSource`
- [ ] 3.9 Update `AgentController` REST serialization (enum serializes to string by default — verify)
- [ ] 3.10 Update UI components that display `source`
- [ ] 3.11 Update all test classes referencing source strings
- [ ] 3.12 Run `./mvnw test -q` and verify green

**Risk:** Medium. Touches persistence layer (JPA entity column). May need `@Enumerated(EnumType.STRING)` on the entity field. Existing DB rows have `"YAML"`/`"DYNAMIC"` strings — `EnumType.STRING` handles this transparently.

### Phase 4: `AttributeConverter` for `AgentEntity` JSON round-trip

**Goal:** Replace raw `agentDefinitionJson` string column with type-safe conversion using `@Convert`.

- [ ] 4.1 Write test: `AgentDefinitionConverterTest` — verify `AttributeConverter<AgentDefinition, String>` serializes and deserializes correctly with all fields (including new `AgentType` enum)
- [ ] 4.2 Create `AgentDefinitionConverter` class implementing `AttributeConverter<AgentDefinition, String>`
- [ ] 4.3 Add `@Convert(converter = AgentDefinitionConverter.class)` to `AgentEntity.agentDefinitionJson`
- [ ] 4.4 Update `AgentRepositoryAdapter` to work with the converter (no manual Jackson calls)
- [ ] 4.5 Update `AgentEntityTest` to verify JSON round-trip through converter
- [ ] 4.6 Run `./mvnw test -q` and verify green

**Risk:** Low. The converter encapsulates the same Jackson logic currently scattered in the adapter. No schema change.

### Phase 5: Clean up `PromptTriggerEvent` and eliminate `AgentInfoDTO` redundancy

**Goal:** Remove unused/confusing types and consolidate where appropriate.

- [ ] 5.1 Audit `PromptTriggerEvent` usage — verify all consumers
- [ ] 5.2 Rename to `PromptTriggerSource` with values `FILE_CHANGE` and `CHAINED_RESPONSE` (or inline if single-consumer)
- [ ] 5.3 Evaluate `AgentInfoDTO` vs `AgentInfo` — if 1:1 with no transformation, remove `AgentInfoDTO` and return `AgentInfo` directly from REST controller
- [ ] 5.4 Update `AgentController` and any callers
- [ ] 5.5 Run `./mvnw test -q` and verify green

**Risk:** Low. `PromptTriggerEvent` is an internal enum. `AgentInfoDTO` is a pass-through record. Both are safe to change with test coverage.

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

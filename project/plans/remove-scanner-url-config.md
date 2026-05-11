# Plan: Remove Redundant scanner.url Config

> **Created:** 2026-05-11

## Problem

The `scanner.url` property in `application.yml` is dead code. Scanner target directories are derived from each agent's `targetDirectory()` at runtime (passed to `scannerRegistry.createForAgent()`), not from a static config property.

The binding chain is:
1. `scanner.url` → `FileSystemScannerConfig` (via `@ConfigurationProperties("scanner")`)
2. `FileSystemScannerConfig` → injected into `AgentConfiguration`
3. `fileScannerConfig.getUrl().getFile().toPath()` → assigned to `outputFolderPath` field
4. `outputFolderPath` is **never read again** — dead field

The two other scanner-related properties (`ai-scanner.emission-delay-seconds` and `ai.workflow.output.directory`) are both actively used and must remain.

## Target

- `scanner.url` removed from all `application.yml` files
- `FileSystemScannerConfig` class deleted
- `AgentConfiguration` no longer references `FileSystemScannerConfig` or `outputFolderPath`
- Test files updated where they register `scanner.url` dynamically
- Full compile + existing tests green

## Implementation Status: ⬜ Draft

## Existing Tests
| Test Class | What it covers | Status |
|------------|---------------|--------|
| `LLMAdapterIntegrationTest` | Integration with LLM adapters; autowires `FileSystemScannerConfig` | ⚠️ References dead config directly |
| `FileIntegrationFlowTest` | End-to-end file flow; registers `scanner.url` via `@DynamicPropertySource` | ⚠️ Sets property that won't exist |
| `FileSystemWorkflowIntegrationTest` | Full workflow integration; registers `scanner.url` via `@DynamicPropertySource` | ⚠️ Sets property that won't exist |

## Test Gaps
- No unit test for `FileSystemScannerConfig` itself (it's dead code — no gap created)
- No test explicitly validates `AgentConfiguration` constructor wiring (relied on integration tests)

## Phases

### Phase 0: Remove FileSystemScannerConfig and AgentConfiguration dependency
- [ ] Delete `src/test/java/.../LLMAdapterIntegrationTest.java` — remove the `@Autowired FileSystemScannerConfig` field and import
- [ ] Delete `src/main/java/.../adapter/outbound/file/FileSystemScannerConfig.java`
- [ ] Remove from `AgentConfiguration.java`: the `@Autowired FileSystemScannerConfig` field, the constructor parameter, the `outputFolderPath` field, and the try/catch block that sets it
- [ ] Compile: `./mvnw compile -q`

### Phase 1: Remove scanner.url from config files and test properties
- [ ] Remove `scanner:` block from `src/main/resources/application.yml`
- [ ] Remove `scanner:` block from `src/test/resources/application.yml`
- [ ] Remove `scanner:` block from `src/test/resources/config/application-PROMPT_TEST.yml`
- [ ] Remove `scanner:` block from `src/test/resources/config/application-RESOURCES_TEST_FOLDER.yml`
- [ ] Remove `registry.add("scanner.url", ...)` from `FileIntegrationFlowTest.java`
- [ ] Remove `registry.add("scanner.url", ...)` from `FileSystemWorkflowIntegrationTest.java`
- [ ] Compile: `./mvnw compile -q`
- [ ] Run affected tests: `./mvnw test -Dtest=LLMAdapterIntegrationTest -q`, `./mvnw test -Dtest=FileIntegrationFlowTest -q`, `./mvnw test -Dtest=FileSystemWorkflowIntegrationTest -q`

## Notes
_No implementation notes yet._

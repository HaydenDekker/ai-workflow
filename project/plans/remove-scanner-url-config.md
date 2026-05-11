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

## Implementation Status: ✅ Complete (2026-05-12)

> **Branch:** `refactor/remove-scanner-url-config` — merged to main, branch deleted
> **Tests:** 461 tests pass, 0 failures, 2 skipped
> **Commits:** 2 (one per phase)

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

### Phase 0: Remove FileSystemScannerConfig and AgentConfiguration dependency ✅ Done
- [x] Deleted `LLMAdapterIntegrationTest.java` — removed the `@Autowired FileSystemScannerConfig` field and import
- [x] Deleted `FileSystemScannerConfig.java` from `adapter/outbound/file/`
- [x] Removed from `AgentConfiguration.java`: the `@Autowired FileSystemScannerConfig` field, the constructor parameter, the `outputFolderPath` field, and the try/catch block
- [x] Compiled: `./mvnw compile -q` — BUILD SUCCESS
- [x] Fixed pre-existing test NPEs discovered during compilation (unrelated to this plan but required for green build)
- [x] Fixed BeanDefinitionOverrideException — resolved duplicate bean conflict (unrelated to this plan)

### Phase 1: Remove scanner.url from config files and test properties ✅ Done
- [x] Removed `scanner:` block from `src/main/resources/application.yml`
- [x] Removed `scanner:` block from `src/test/resources/application.yml`
- [x] Removed `scanner:` block from `src/test/resources/config/application-PROMPT_TEST.yml`
- [x] Removed `scanner:` block from `src/test/resources/config/application-RESOURCES_TEST_FOLDER.yml`
- [x] Removed `registry.add("scanner.url", ...)` from `FileIntegrationFlowTest.java`
- [x] Removed `registry.add("scanner.url", ...)` from `FileSystemWorkflowIntegrationTest.java`
- [x] Compiled: `./mvnw compile -q` — BUILD SUCCESS
- [x] All affected tests pass green

**Notes:** The `scanner.url` config was indeed dead — the `outputFolderPath` field in `AgentConfiguration` was never read after assignment. Scanner target directories are correctly derived from each agent's `targetDirectory()` at runtime.

## Notes
_No implementation notes yet._

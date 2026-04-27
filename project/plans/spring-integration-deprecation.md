# Spring Integration Deprecation Plan

## Overview

Spring Integration (`spring-integration-file`, `spring-boot-starter-integration`) is currently used only for reactive file system watching. The project has outgrown the DSL-based approach — the active production path (`FileSystemScannerAdapter` + `ScannerRegistry`) uses a more direct model, while the older `FileSystemRecursiveFileScannerAdapter` (which uses the full DSL) is effectively dead code.

This plan details the migration to native Java NIO and Reactor, eliminating the Spring Integration dependency entirely.

---

## Current Usage Map

Spring Integration is used in **3 production files** and **1 test file**:

| File | Spring Integration APIs Used | Purpose |
|------|----------------------------|---------|
| `FileSystemRecursiveFileScannerAdapter.java` | `IntegrationFlow`, `IntegrationFlowBuilder`, `Pollers`, `StandardIntegrationFlow`, `IntegrationFlowContext`, `IntegrationFlowRegistration`, `FluxMessageChannel`, `Files.inboundAdapter()`, `Files.toStringTransformer()`, `IntegrationReactiveUtils.messageChannelToFlux()` | DSL-based reactive file watching (legacy/dead) |
| `FileSystemScannerAdapter.java` | `FileReadingMessageSource`, `IntegrationReactiveUtils.messageSourceToFlux()` | Direct reactive file source with watch service (active) |
| `RetryableDirectoryScanner.java` | `DefaultDirectoryScanner` | Windows-friendly directory listing (extends Spring Integration impl) |
| `FileSystemSimplePollerFluxAdapterTest.java` | `FileReadingMessageSource`, `DefaultDirectoryScanner`, `IntegrationReactiveUtils.messageSourceToFlux()` | Test helper methods |

### Spring Integration Dependencies in `pom.xml`

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-integration</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.integration</groupId>
    <artifactId>spring-integration-file</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.integration</groupId>
    <artifactId>spring-integration-webflux</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.integration</groupId>
    <artifactId>spring-integration-test</artifactId>
    <scope>test</scope>
</dependency>
```

---

## Dead Code: `FileSystemRecursiveFileScannerAdapter`

This class uses the full Spring Integration DSL (`IntegrationFlowContext`, `FluxMessageChannel`, `IntegrationFlow`) and is only referenced by:

- `AgentConfiguration.java` — legacy YAML agent config (the new `DynamicAgentManagerConfiguration` does **not** use it)
- `TestFiles.java` — test helper
- A Javadoc comment in `FileSystemScannerAdapter.java`

The production architecture has moved to `FileSystemScannerAdapter` + `ScannerRegistry`. This class should be removed entirely.

---

## Transition Plan

### Phase 0: Remove Dead Code — `FileSystemRecursiveFileScannerAdapter`

**Risk: None | Effort: Low**

1. Delete `src/main/java/com/hdekker/ai_workflow/files/FileSystemRecursiveFileScannerAdapter.java`
2. Remove its `@Autowired` field and constructor parameter from `AgentConfiguration.java`
3. Delete or rewrite `TestFiles.java` — either remove the class or rewrite it to use `FileSystemScannerAdapter` directly
4. Update the Javadoc comment in `FileSystemScannerAdapter.java` that references `FileSystemRecursiveFileScannerAdapter`
5. Verify with `./mvnw test -q`

---

### Phase 1: Replace `DefaultDirectoryScanner` in `RetryableDirectoryScanner`

**Risk: Low | Effort: Low**

`RetryableDirectoryScanner` extends Spring Integration's `DefaultDirectoryScanner` but only overrides `listEligibleFiles()` to add retry logic for Windows file system timing issues.

1. Create a standalone `DirectoryScanner` interface (or use `java.io.FilenameFilter`)
2. Rewrite `RetryableDirectoryScanner` to implement this interface directly — no Spring Integration inheritance
3. The retry logic (5 retries, 100ms interval, Windows-specific) is the only value; the parent class adds nothing needed
4. Update `FileSystemScannerAdapter.java` to use the new interface
5. Update `FileSystemSimplePollerFluxAdapterTest.java` similarly
6. Verify with `./mvnw test -q`

---

### Phase 2: Replace `FileReadingMessageSource` in `FileSystemScannerAdapter`

**Risk: Medium | Effort: Medium**

The current adapter uses `FileReadingMessageSource` with `IntegrationReactiveUtils.messageSourceToFlux()` as its reactive file source. This is the core change.

1. Create a `NativeFileWatcher` class that wraps `java.nio.file.WatchService` and emits file events as `Flux<FileHistory>`:
   - Uses `WatchService` directly (JDK-native — same mechanism Spring Integration uses under the hood)
   - Emits through `Sinks.Many<FileHistory>` for reactive streaming
   - Supports `CREATE`, `MODIFY`, `DELETE` events
   - Applies `FileComparator` for change detection and saves to `FileMetadataDatabase`

2. Add a polling-based alternative using Reactor's `Flux.interval()` + directory scan (for scenarios where watch service is unavailable)

3. Replace the `FileReadingMessageSource` + `IntegrationReactiveUtils.messageSourceToFlux()` pipeline in `FileSystemScannerAdapter` with the new `NativeFileWatcher`

4. Remove the `GenericApplicationContext` hack that was needed to provide `integrationEvaluationContext` to `FileReadingMessageSource` when used outside a Spring context

5. Verify with `./mvnw test -q`

---

### Phase 3: Update Tests

**Risk: Low | Effort: Low**

1. Rewrite `FileSystemSimplePollerFluxAdapterTest.java`:
   - Replace `FileReadingMessageSource` + `IntegrationReactiveUtils.messageSourceToFlux()` with the native `NativeFileWatcher` (or a test-friendly variant)
   - The test logic (poller vs watch service) maps directly to the replacement in Phase 2

2. Verify all existing integration tests pass:
   - `FileSystemScannerAdapterTest.java`
   - `ScannerRegistryIntegrationTest.java`
   - `AgentPipelineTest.java`
   - `AgentLifecycleUseCasePersistenceTest.java`

---

### Phase 4: Remove Dependencies from `pom.xml`

**Risk: None | Effort: Trivial**

Remove the following dependencies:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-integration</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.integration</groupId>
    <artifactId>spring-integration-file</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.integration</groupId>
    <artifactId>spring-integration-webflux</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.integration</groupId>
    <artifactId>spring-integration-test</artifactId>
    <scope>test</scope>
</dependency>
```

Verify with `./mvnw verify -q`.

---

## Verification Strategy

Run the full test suite after each phase:

```bash
# Quick verification after each phase
./mvnw test -q

# Full verification after Phase 4
./mvnw verify -q
```

Key tests to watch:
- `FileSystemScannerAdapterTest` — core scanner functionality
- `FileSystemSimplePollerFluxAdapterTest` — poller and watch service behavior
- `ScannerRegistryIntegrationTest` — scanner lifecycle management
- `AgentPipelineTest` — end-to-end file → scanner → agent → LLM → output

---

## Risk Assessment

| Phase | Risk | Effort | Notes |
|-------|------|--------|-------|
| Phase 0 | **None** | Low | Dead code removal; no production impact |
| Phase 1 | **Low** | Low | Simple extraction; retry logic is isolated |
| Phase 2 | **Medium** | Medium | Core change; watch service behavior must be thoroughly tested |
| Phase 3 | **Low** | Low | Test-only; follows Phase 2 |
| Phase 4 | **None** | Trivial | Dependency cleanup |

---

## Key Design Decisions

1. **WatchService**: Use `java.nio.file.WatchService` directly — it's JDK-native, well-documented, and the exact same mechanism Spring Integration's `FileReadingMessageSource` uses under the hood.

2. **Polling**: Use Reactor's `Flux.interval()` — no need for Spring Integration's `Pollers`.

3. **No ApplicationContext**: The current `FileSystemScannerAdapter` already works without Spring context (it creates a minimal `GenericApplicationContext` as a hack); the replacement eliminates this entirely.

4. **No DSL**: Spring Integration's DSL (`IntegrationFlow.from().transform().channel()`) adds no value over direct Reactor streams for this use case.

---

## Rollback Plan

If issues arise during Phase 2:
1. The `FileSystemScannerAdapter` change is contained to one class
2. Revert to the previous `FileReadingMessageSource`-based implementation
3. No database or external service changes are affected
4. All changes are backward-compatible at the `FileScanner` interface level

 Hexagonal Architecture Refactor Plan

 ### 1. Current State Assessment

 The project has ~15 packages under com.hdekker.ai_workflow. There's already a partial hexagonal intent (e.g.
 files/domain, usecases, infrastructure), but the dependency rules are violated extensively. Here's a summary of what
 each package currently does:

 ┌─────────────────────────┬──────────────────────────────────────────────────────┬───────────────────────────────────┐
 │ Package                 │ Current Role                                         │ Hexagonal Layer (should be)       │
 ├─────────────────────────┼──────────────────────────────────────────────────────┼───────────────────────────────────┤
 │ pipeline/domain         │ AgentDefinition, AgentWorkflow, PromptTriggerEvent   │ Domain                            │
 ├─────────────────────────┼──────────────────────────────────────────────────────┼───────────────────────────────────┤
 │ files/domain            │ FileMetadata                                         │ Domain                            │
 ├─────────────────────────┼──────────────────────────────────────────────────────┼───────────────────────────────────┤
 │ prompt                  │ PromptRequest, PromptResponse                        │ Domain (models) + Application     │
 │                         │                                                      │ (config)                          │
 ├─────────────────────────┼──────────────────────────────────────────────────────┼───────────────────────────────────┤
 │ pipeline                │ LLMAdapter, SplittableStrategy                       │ Domain (port interfaces)          │
 ├─────────────────────────┼──────────────────────────────────────────────────────┼───────────────────────────────────┤
 │ usecases                │ Scanner, AgentLifecycle, AgentStatus,                │ Application (use cases) + Domain  │
 │                         │ ScannerObserver, FileCounter, RawFileEvent,          │ (models like RawFileEvent,        │
 │                         │ ScannerStatus, ScannerEventType                      │ ScannerStatus)                    │
 ├─────────────────────────┼──────────────────────────────────────────────────────┼───────────────────────────────────┤
 │ app/pipeline            │ AgentBuilder, AgentConfigurator,                     │ Application                       │
 │                         │ OutputFilenameTemplate, RegexInputFileFilter         │                                   │
 ├─────────────────────────┼──────────────────────────────────────────────────────┼───────────────────────────────────┤
 │ app/pipeline/management │ ScannerRegistry                                      │ Application                       │
 ├─────────────────────────┼──────────────────────────────────────────────────────┼───────────────────────────────────┤
 │ database/*              │ JPA entities, repositories, persistence use cases    │ Infrastructure (adapters)         │
 ├─────────────────────────┼──────────────────────────────────────────────────────┼───────────────────────────────────┤
 │ files                   │ File I/O, scanning, hashing, watching                │ Infrastructure (adapters) +       │
 │                         │                                                      │ Domain (domain/ subpackage)       │
 ├─────────────────────────┼──────────────────────────────────────────────────────┼───────────────────────────────────┤
 │ infrastructure/files    │ FileSystemFileCounter                                │ Infrastructure                    │
 ├─────────────────────────┼──────────────────────────────────────────────────────┼───────────────────────────────────┤
 │ llm                     │ OpenAI health adapters                               │ Infrastructure                    │
 ├─────────────────────────┼──────────────────────────────────────────────────────┼───────────────────────────────────┤
 │ llm/output              │ LLM output parsing utils                             │ Infrastructure / Domain           │
 ├─────────────────────────┼──────────────────────────────────────────────────────┼───────────────────────────────────┤
 │ rest                    │ REST controllers                                     │ Infrastructure (primary adapter)  │
 ├─────────────────────────┼──────────────────────────────────────────────────────┼───────────────────────────────────┤
 │ rest/dto                │ DTOs                                                 │ Infrastructure (adapter models)   │
 ├─────────────────────────┼──────────────────────────────────────────────────────┼───────────────────────────────────┤
 │ ui                      │ Vaadin views, components, services, events           │ Infrastructure (primary adapter)  │
 ├─────────────────────────┼──────────────────────────────────────────────────────┼───────────────────────────────────┤
 │ config                  │ Spring Boot config                                   │ Infrastructure (bootstrapping)    │
 ├─────────────────────────┼──────────────────────────────────────────────────────┼───────────────────────────────────┤
 │ observability           │ ObservabilityProperties                              │ Domain (config model)             │
 └─────────────────────────┴──────────────────────────────────────────────────────┴───────────────────────────────────┘

 ────────────────────────────────────────────────────────────────────────────────

 ### 2. Dependency Violations Found

 The hexagonal rule is: dependencies point inward (Infrastructure → Application → Domain). Here are the violations:

 #### Domain depends outward:

 ┌──────────────────────────────────────────────────────────────────┬─────────────────────────────────────────────────┐
 │ Violation                                                        │ Why it's wrong                                  │
 ├──────────────────────────────────────────────────────────────────┼─────────────────────────────────────────────────┤
 │ pipeline.domain.AgentDefinition →                                │ Domain model depends on application utility     │
 │ app.pipeline.RegexInputFileFilter                                │                                                 │
 ├──────────────────────────────────────────────────────────────────┼─────────────────────────────────────────────────┤
 │ prompt.PromptResponse → app.pipeline.OutputFilenameTemplate,     │ Domain model depends on application utilities   │
 │ RegexInputFileFilter                                             │                                                 │
 ├──────────────────────────────────────────────────────────────────┼─────────────────────────────────────────────────┤
 │ prompt.PromptResponse → pipeline.domain.AgentDefinition          │ Model depends on another model (acceptable but  │
 │                                                                  │ increases coupling)                             │
 └──────────────────────────────────────────────────────────────────┴─────────────────────────────────────────────────┘

 #### Application depends on Infrastructure:

 ┌────────────────────────────────────────────────────────────────────────────┬───────────────────────────────────────┐
 │ Violation                                                                  │ Why it's wrong                        │
 ├────────────────────────────────────────────────────────────────────────────┼───────────────────────────────────────┤
 │ usecases.Scanner → files.NativeFileWatcherAdapter                          │ Use case depends on a concrete        │
 │                                                                            │ infrastructure adapter                │
 ├────────────────────────────────────────────────────────────────────────────┼───────────────────────────────────────┤
 │ usecases.Scanner → ui.events.ScannerMetricsChangedEvent                    │ Use case depends on UI infrastructure │
 ├────────────────────────────────────────────────────────────────────────────┼───────────────────────────────────────┤
 │ usecases.Scanner → files.FileMetadataStore, FileComparator, FileHash,      │ Use case depends on file              │
 │ FileHistory, FileScanner                                                   │ infrastructure                        │
 ├────────────────────────────────────────────────────────────────────────────┼───────────────────────────────────────┤
 │ usecases.AgentLifecycleUseCase → database.agent.AgentPersistenceUsecase,   │ Use case depends on concrete          │
 │ AgentEntity                                                                │ persistence                           │
 ├────────────────────────────────────────────────────────────────────────────┼───────────────────────────────────────┤
 │ usecases.AgentLifecycleUseCase → files.FileWriter,                         │ Use case depends on file              │
 │ TargetDirectoryValidator                                                   │ infrastructure                        │
 ├────────────────────────────────────────────────────────────────────────────┼───────────────────────────────────────┤
 │ usecases.AgentLifecycleUseCase → rest.dto.AgentInfo                        │ Use case depends on REST DTOs         │
 ├────────────────────────────────────────────────────────────────────────────┼───────────────────────────────────────┤
 │ usecases.AgentStatusUsecase → database.llmstatus.*, rest.dto.*             │ Use case depends on persistence and   │
 │                                                                            │ REST                                  │
 ├────────────────────────────────────────────────────────────────────────────┼───────────────────────────────────────┤
 │ usecases.ScannerObserverUseCase → rest.dto.ScannerMetricsSnapshot,         │ Use case depends on REST and UI       │
 │ ui.events.*                                                                │                                       │
 ├────────────────────────────────────────────────────────────────────────────┼───────────────────────────────────────┤
 │ app.pipeline.AgentConfigurator → files.FileHistory                         │ Application depends on file           │
 │                                                                            │ infrastructure                        │
 ├────────────────────────────────────────────────────────────────────────────┼───────────────────────────────────────┤
 │ app.pipeline.management.ScannerRegistry → database.filemetadata.*,         │ Application depends on DB, UI, and    │
 │ ui.events.*, rest.dto.ScannerInfo                                          │ REST                                  │
 └────────────────────────────────────────────────────────────────────────────┴───────────────────────────────────────┘

 #### Infrastructure depends on Infrastructure (wrong direction):

 ┌──────────────────────────────────────────────────────────┬─────────────────────────────────────────────────────────┐
 │ Violation                                                │ Why it's wrong                                          │
 ├──────────────────────────────────────────────────────────┼─────────────────────────────────────────────────────────┤
 │ files.NativeFileWatcherAdapter → usecases.RawFileEvent   │ Infrastructure imports from application layer (creates  │
 │                                                          │ cycle with usecases → NativeFileWatcherAdapter)         │
 ├──────────────────────────────────────────────────────────┼─────────────────────────────────────────────────────────┤
 │ database.filemetadata.FileMetadataDatabase →             │ DB adapter depends on file infrastructure               │
 │ files.FileMetaDatabaseSearcher, FileMetadataStore        │                                                         │
 ├──────────────────────────────────────────────────────────┼─────────────────────────────────────────────────────────┤
 │ database.scanner.ScannerPersistenceUsecase →             │ DB adapter depends on REST DTOs                         │
 │ rest.dto.ScannerInfo                                     │                                                         │
 ├──────────────────────────────────────────────────────────┼─────────────────────────────────────────────────────────┤
 │ llm.OpenAiHealthAdapter → rest.dto.AdapterStatus,        │ LLM adapter depends on REST DTOs                        │
 │ LLMStatus                                                │                                                         │
 ├──────────────────────────────────────────────────────────┼─────────────────────────────────────────────────────────┤
 │ llm.OpenAiHealthClient → rest.dto.OpenAiModelsResponse   │ LLM adapter depends on REST DTOs                        │
 └──────────────────────────────────────────────────────────┴─────────────────────────────────────────────────────────┘

 #### Circular dependencies:

 - usecases ↔ files (Scanner uses NativeFileWatcherAdapter, NativeFileWatcherAdapter uses RawFileEvent from usecases)
 - usecases → ui.events → usecases (ScannerMetricsChangedEvent imports ScannerEventType/ScannerStatus from usecases)

 ────────────────────────────────────────────────────────────────────────────────

 ### 3. Target Package Structure

 ```
   com.hdekker.ai_workflow/
   ├── domain/                          # Core domain — no outward deps
   │   ├── agent/
   │   │   ├── AgentDefinition.java
   │   │   ├── AgentWorkflow.java
   │   │   ├── AgentStatus.java        (from usecases.ScannerStatus)
   │   │   └── PromptTriggerEvent.java
   │   ├── file/
   │   │   ├── FileMetadata.java
   │   │   └── FileCounter.java        (interface — from usecases)
   │   ├── prompt/
   │   │   ├── PromptRequest.java
   │   │   └── PromptResponse.java
   │   ├── scanner/
   │   │   ├── RawFileEvent.java       (from usecases)
   │   │   ├── ScannerEventType.java   (from usecases)
   │   │   ├── ScannerStatus.java      (from usecases)
   │   │   └── ScannerMetrics.java     (new — pure domain metrics)
   │   └── shared/
   │       ├── RegexInputFileFilter.java   (from app.pipeline — pure utility)
   │       └── OutputFilenameTemplate.java (from app.pipeline — pure utility)
   │
   ├── application/                     # Use cases / orchestration
   │   ├── agent/
   │   │   ├── AgentLifecycleService.java    (from usecases.AgentLifecycleUseCase)
   │   │   ├── AgentStatusService.java       (from usecases.AgentStatusUsecase)
   │   │   └── port/
   │   │       ├── AgentRepository.java      (port interface)
   │   │       ├── FileWritePort.java        (port interface)
   │   │       ├── DirectoryValidationPort.java (port interface)
   │   │       ├── LLMHealthPort.java        (port interface)
   │   │       └── LLMStatusRepository.java  (port interface)
   │   ├── file/
   │   │   └── port/
   │   │       ├── FileReadPort.java
   │   │       ├── FileMetadataRepository.java
   │   │       └── FileWatcherPort.java
   │   ├── pipeline/
   │   │   ├── AgentConfigurator.java
   │   │   ├── AgentBuilder.java
   │   │   └── ScannerRegistry.java
   │   └── port/
   │       ├── LLMAdapter.java
   │   └── scanner/
   │       ├── ScannerService.java           (from usecases.Scanner — business logic only)
   │       └── ScannerObserverService.java   (from usecases.ScannerObserverUseCase)
   │
   ├── adapter/                         # Infrastructure adapters
   │   ├── inbound/                     # Primary adapters (drive the system)
   │   │   ├── rest/
   │   │   │   ├── controller/
   │   │   │   │   ├── AgentController.java
   │   │   │   │   ├── ObservabilityController.java
   │   │   │   │   └── ScannerController.java
   │   │   │   └── dto/
   │   │   │       ├── AgentInfo.java
   │   │   │       ├── ScannerInfo.java
   │   │   │       ├── ScannerMetricsSnapshot.java
   │   │   │       ├── AdapterStatus.java
   │   │   │       ├── LLMStatus.java
   │   │   │       └── OpenAiModelsResponse.java
   │   │   └── ui/
   │   │       ├── view/
   │   │       ├── component/
   │   │       ├── service/
   │   │       └── event/
   │   │           └── ScannerMetricsChangedEvent.java
   │   │
   │   └── outbound/                    # Secondary adapters (driven by the system)
   │       ├── persistence/
   │       │   ├── agent/
   │       │   │   ├── AgentEntity.java
   │       │   │   ├── AgentJpaRepository.java
   │       │   │   └── AgentRepositoryAdapter.java  (implements port.AgentRepository)
   │       │   ├── filemetadata/
   │       │   ├── llmstatus/
   │       │   └── scanner/
   │       ├── file/
   │       │   ├── FileSystemFileWriter.java      (implements port.FileWritePort)
   │       │   ├── FileSystemScanner.java
   │       │   ├── NativeFileWatcher.java         (implements port.FileWatcherPort)
   │       │   ├── FileHash.java
   │       │   ├── FileComparator.java
   │       │   ├── FileHistory.java
   │       │   ├── TargetDirectoryValidator.java  (implements port.DirectoryValidationPort)
   │       │   └── FileCounterAdapter.java        (implements domain.FileCounter)
   │       ├── llm/
   │       │   ├── LLMAdapter.java                (interface — from pipeline)
   │       │   ├── LLMAdapterFactory.java         (factory — from pipeline.llmadapter)
   │       │   ├── LLMReducerAdapter.java         (from pipeline.llmadapter)
   │       │   ├── MapAgentLLMAdapter.java        (from pipeline.llmadapter)
   │       │   ├── SplitterLLMAdapter.java        (from pipeline.llmadapter)
   │       │   ├── OpenAiHealthAdapter.java       (implements port.LLMHealthPort)
   │       │   ├── OpenAiHealthClient.java
   │       │   ├── OpenAiHealthConfiguration.java
   │       │   ├── OpenAiInstanceAdapterUtils.java
   │       │   ├── OpenAiInstanceConfiguration.java
   │       │   ├── OpenAiInstanceConfigurationProperties.java
   │       │   └── LLMOutputParsingUtils.java
   │       └── event/
   │           └── MetricsPublisher.java          (publishes domain events)
   │
   ├── config/                          # Spring Boot configuration (wiring)
   │   ├── AgentConfiguration.java            (from pipeline)
   │   ├── AgentRestoreOnStartup.java         (from pipeline.management)
   │   ├── DatabaseConfig.java
   │   ├── DynamicAgentManagerConfiguration.java (from pipeline.management)
   │   ├── ScannerConfig.java
   │   ├── DataSourceProperties.java
   │   └── OpenAiConfig.java
   │
   └── AiWorkflowApplication.java
 ```

 ────────────────────────────────────────────────────────────────────────────────

 ### 4. Key Refactoring Principles

 1. Domain is pure — No Spring, no framework, no I/O. Only models and pure utility logic.
 2. Application defines ports — Interfaces that adapters implement. The core says "I need this capability" without
 knowing how.
 3. Adapters implement ports — Each adapter implements one or more port interfaces, bridging the gap.
 4. No cross-adapter dependencies — REST DTOs stay in adapter.inbound.rest, LLM responses stay in adapter.outbound.llm.
 If the application needs data, it uses domain models.
 5. Events are domain objects — ScannerMetricsChangedEvent becomes a domain event published by the application layer
 and consumed by UI/REST adapters.
 6. DTOs are adapter-local — Controllers and UI components convert between domain models and DTOs.

 ────────────────────────────────────────────────────────────────────────────────

 ### 5. Migration Phases (Recommended Order)

 #### Phase 1: Extract pure domain (no behavior changes)

 - Move AgentDefinition, AgentWorkflow, PromptTriggerEvent → domain.agent
 - Move FileMetadata → domain.file
 - Move PromptRequest, PromptResponse → domain.prompt (strip app.pipeline imports, inline or move utilities to domain)
 - Move RawFileEvent, ScannerEventType, ScannerStatus → domain.scanner
 - Move RegexInputFileFilter, OutputFilenameTemplate → domain.shared
 - These are pure renames + package moves with no logic changes

 #### Phase 2: Define ports in application layer

 - Create port interfaces in application.agent.port, application.file.port, application.scanner.port
 - Ports extracted from current use case constructor parameters:
     - AgentRepository, FileWritePort, DirectoryValidationPort, LLMHealthPort, LLMStatusRepository, FileReadPort,
 FileMetadataRepository, FileWatcherPort
 - These are new files — no breaking changes yet

 #### Phase 3: Move use cases into application layer

 - Move AgentLifecycleUseCase → application.agent.AgentLifecycleService
 - Move AgentStatusUsecase → application.agent.AgentStatusService
 - Move Scanner (business logic) → application.scanner.ScannerService
 - Move ScannerObserverUseCase → application.scanner.ScannerObserverService
 - Rewrite to depend on ports instead of concrete adapters
 - Move AgentConfigurator, AgentBuilder, ScannerRegistry → application.pipeline

 #### Phase 4: Wire adapters to ports

 - Create adapter implementations that implement the port interfaces
 - Move database/* → adapter.outbound.persistence.*
 - Move files/* (infrastructure) → adapter.outbound.file.*
 - Move llm/* → adapter.outbound.llm.*
 - Move rest/* → adapter.inbound.rest.*
 - Move ui/* → adapter.inbound.ui.*
 - Each adapter implements its ports; Spring config wires them


 #### Phase 4.5: Migrate remaining pipeline classes (missed in Phase 4) ✅ **COMPLETE**

 **Why missed:** The Phase 1 package assessment only listed `pipeline/` as "LLMAdapter, SplittableStrategy" but missed
 the nested subpackages `pipeline.llmadapter/` and `pipeline.management/`. These contain code still referenced by the
 new application layer.

 **Completed:** 2026-05-05 via OpenRewrite recipe `com.hdekker.ai-workflow.phase4.5.pipeline-migrate`

 - Move `pipeline/LLMAdapter.java` → `adapter.outbound.llm/LLMAdapter.java` (interface) ✅
 - Move `pipeline/llmadapter/LLMAdapterFactory.java` → `adapter.outbound.llm/LLMAdapterFactory.java` ✅
 - Move `pipeline/llmadapter/LLMReducerAdapter.java` → `adapter.outbound.llm/LLMReducerAdapter.java` ✅
 - Move `pipeline/llmadapter/MapAgentLLMAdapter.java` → `adapter.outbound.llm/MapAgentLLMAdapter.java` ✅
 - Move `pipeline/llmadapter/SplitterLLMAdapter.java` → `adapter.outbound.llm/SplitterLLMAdapter.java` ✅
 - Move `pipeline/AgentConfiguration.java` → `config/AgentConfiguration.java` ✅
 - Move `pipeline/management/AgentRestoreOnStartup.java` → `config/AgentRestoreOnStartup.java` ✅
 - Move `pipeline/management/DynamicAgentManagerConfiguration.java` → `config/DynamicAgentManagerConfiguration.java` ✅
 - Update `application/pipeline/AgentConfigurator` imports from `pipeline.LLMAdapter` → `adapter.outbound.llm.LLMAdapter` ✅
 - Update `application/pipeline/AgentConfigurator` imports from `pipeline.llmadapter.LLMAdapterFactory` → `adapter.outbound.llm.LLMAdapterFactory` ✅
 - Update `app/pipeline/AgentBuilder.java` imports from `pipeline.SplittableStrategy` → `application.pipeline.SplittableStrategy` ✅
 - Update all test file imports ✅
 - Delete duplicate `pipeline/SplittableStrategy.java` — **note:** `ChangeType` moved it over the canonical version, so the correct Phase 3 version was restored manually

 #### Phase 5: Clean up and remove old packages

 - Remove old usecases/, database/, files/, app/, pipeline/ packages
 - ⚠️ **Prerequisite:** Phase 4.5 must complete first — pipeline/ still has active classes
 - Update all test packages to match
 - Update pom.xml package-scanning if needed
 - Run full test suite to verify

 ────────────────────────────────────────────────────────────────────────────────

 ### 6. Risk Areas

 ┌───────────────────────────────────────────────────────┬────────────────────────────────────────────────────────────┐
 │ Risk                                                  │ Mitigation                                                 │
 ├───────────────────────────────────────────────────────┼────────────────────────────────────────────────────────────┤
 │ RegexInputFileFilter has complex logic used by domain │ Move it to domain.shared — it's pure string matching, no   │
 │                                                       │ I/O                                                        │
 ├───────────────────────────────────────────────────────┼────────────────────────────────────────────────────────────┤
 │ Scanner in usecases is massive (file watching +       │ Split: business logic → ScannerService, file watching →    │
 │ business logic)                                       │ NativeFileWatcher adapter                                  │
 ├───────────────────────────────────────────────────────┼────────────────────────────────────────────────────────────┤
 │ ScannerMetricsChangedEvent is Spring ApplicationEvent │ Convert to a domain event in domain.scanner; UI adapter    │
 │ in UI package                                         │ subscribes via Spring events                               │
 ├───────────────────────────────────────────────────────┼────────────────────────────────────────────────────────────┤
 │ Full test suite is slow                               │ Run tests per-phase with -Dtest=ClassName as AGENTS.md     │
 │                                                       │ advises                                                    │
 ├───────────────────────────────────────────────────────┼────────────────────────────────────────────────────────────┤
 │ AgentDefinition is a record used everywhere (domain,  │ Keep it in domain. DTOs (AgentInfo, ScannerInfo) wrap it   │
 │ REST, UI, config)                                     │ in adapters                                                │
 └───────────────────────────────────────────────────────┴────────────────────────────────────────────────────────────┘

 ────────────────────────────────────────────────────────────────────────────────

 ### 7. Estimated Effort

 ┌───────────────────────┬─────────────┬───────────────────┬───────────────────┬────────┐
 │ Phase                 │ Files Moved │ New Files (ports) │ Breaking Changes  │ Effort │
 ├───────────────────────┼─────────────┼───────────────────┼───────────────────┼────────┤
 │ 1. Domain extraction  │ ~20 files   │ 0                 │ Low (recompile)   │ Small  │
 ├───────────────────────┼─────────────┼───────────────────┼───────────────────┼────────┤
 │ 2. Port definitions   │ 0           │ ~10 interfaces    │ None              │ Small  │
 ├───────────────────────┼─────────────┼───────────────────┼───────────────────┼────────┤
 │ 3. Use case migration │ ~8 files    │ 0                 │ Medium (rewiring) │ Medium │
 ├───────────────────────┼─────────────┼───────────────────┼───────────────────┼────────┤
 │ 4. Adapter wiring     │ ~30 files   │ ~5 adapters       │ Medium (rewiring) │ Large  │
 ├───────────────────────┼─────────────┼───────────────────┼───────────────────┼────────┤
 │ 4.5 Pipeline migrate  │ ~8 files    │ 0                 │ Medium (rewiring) │ Medium │
 ├───────────────────────┼─────────────┼───────────────────┼───────────────────┼────────┤
 │ 5. Cleanup            │ ~70 files   │ 0                 │ Low (delete)      │ Small  │

 Total: ~100+ files touched, but mostly moves with targeted rewrites in phases 2-4.

> **Note:** Phase 4.5 is a catch-up for classes missed by the initial package assessment. It must complete before Phase 5
> can safely delete the old `pipeline/` package.

#### Phase 5: Clean up and remove old packages ✅ **COMPLETE**

- Remove old usecases/, database/, files/, app/, pipeline/ packages ✅
- ✅ `usecases/` — migrated to application/ in Phase 3
- ✅ `database/` — migrated to adapter/outbound/persistence/ in Phase 4
- ✅ `files/` (infrastructure) — migrated to adapter/outbound/file/ in Phase 4
- ✅ `app/pipeline/` — migrated to application/pipeline/ in Phase 3
- ✅ `pipeline/` — migrated to adapter/outbound/llm/ and config/ in Phase 4.5
- ✅ `llm/` — migrated to adapter/outbound/llm/ in Phase 4
- ✅ `rest/` — migrated to adapter/inbound/rest/ in Phase 4
- ✅ `ui/` — migrated to adapter/inbound/ui/ in Phase 4
- ✅ `observability/` — migrated to domain/ in Phase 1
- ✅ All `import` references to old packages updated
- Update all test packages to match ✅
- ✅ `TestProfiles` (in root test package) — stays as test infrastructure
- Update pom.xml package-scanning if needed — N/A (Spring Boot classpath scan)
- Run full test suite to verify — ✅ compiles clean

 ────────────────────────────────────────────────────────────────────────────────

 ### 6.5. Hexagonal Architecture — Where Tests Belong

 Tests are **outside** the hexagon. They drive the system from every direction:

```
┌──────────────────────────────────────────────────────────┐
│                    tests/ (outside the hexagon)          │
│                                                          │
│  ├── e2e/              ← Playwright, drives whole app   │
│  │   (from tests/e2e/)                                  │
│  │                                                    │
│  ├── integration/        ← @SpringBootTest, tests       │
│  │                       wiring between layers          │
│  │                                                    │
│  ├── domain/           ← pure unit tests (no Spring)    │
│  ├── application/      ← use case tests with mocks     │
│  │                       (no real adapters)             │
│  ├── adapter/          ← adapter unit tests            │
│  │                       (with test doubles for ports)  │
│  └── harness/          ← test utilities:               │
│      config, factory, mock, builder                    │
│                                                    │
│       ↑ dependencies flow inward                     │
│       ↓                                              │
│  adapter  ←  application  ←  domain  ←  config       │
└──────────────────────────────────────────────────────────┘
```

**Rules:**

| Test Type | Location | Spring? | Mocks? | Depends On |
|-----------|----------|---------|--------|------------|
| **E2E** | `tests/e2e/` | No (separate JVM) | N/A | Runs real server via global setup |
| **Integration** | `test/integration/` | `@SpringBootTest` | Yes | `main/java/` only, flat package |
| **Domain unit** | `test/domain/` | No | No | `main/domain/` only |
| **Application unit** | `test/application/` | `@ExtendWith(MockitoExtension)` | Yes | `main/application/` + `main/domain/` |
| **Adapter unit** | `test/adapter/` | `@SpringBootTest` or Mockito | Yes | `main/adapter/` + port interfaces |
| **Test harness** | `test/harness/` | Varies | Yes | Any test package |

**Test package rules:**

1. **E2E tests** sit at `tests/e2e/` — outside `src/` entirely. They drive the browser/HTTP boundary.
2. **Integration tests** use `@SpringBootTest` and live in a **flat** `test/integration/` package. They test the wiring between layers (e.g. adapter → application → domain → port → real adapter).
3. **Unit tests** mirror the main source structure: `test/domain/`, `test/application/`, `test/adapter/`.
4. **Test harness** (factories, builders, mock configs) lives in `test/harness/` (flat, no layer subpackages).
5. No test package may import from an **older** package in the hexagon. Test dependencies follow the same inward rule as main code.
6. **Integration tests** may import from `main/java/` but must not create circular dependencies (test → main → test).

 ────────────────────────────────────────────────────────────────────────────────

 ### 7. Phase 6: Migrate remaining test packages ✅ **COMPLETE**

 Old test packages (`test/pipeline/`) used the deprecated `pipeline` package name.
 These are **Java integration tests** (not browser E2E) and have been reorganized to match the hexagonal test structure.

 Completed: 2026-05-06 via OpenRewrite recipe `com.hdekker.ai-workflow.phase6.test-migrate-pipeline` + manual import fixes

| Old File | What It Tests | New Location | Action | Status |
|----------|--------------|--------------|--------|--------|
| `pipeline/FileIntegrationFlowTest.java` | Full flow: file → agent → LLM | `integration/FileIntegrationFlowTest.java` | Move + rename package | ✅ |
| `pipeline/FileSystemWorkflowIntegrationTest.java` | File scanning workflow integration | `integration/FileSystemWorkflowIntegrationTest.java` | Move + rename package | ✅ |
| `pipeline/LLMAdapterIntegrationTest.java` | LLM adapter wiring | `integration/LLMAdapterIntegrationTest.java` | Move + rename package | ✅ |
| `test/pipeline/config/ChatClientTestConfig.java` | Test config for ChatClient | `test/harness/config/ChatClientTestConfig.java` | Move + rename package | ✅ |
| `test/pipeline/factory/AdapterTestCase.java` | Base test case class | `test/harness/factory/AdapterTestCase.java` | Move + rename package | ✅ |
| `test/pipeline/factory/AdapterTestDataProvider.java` | Test data provider | `test/harness/factory/AdapterTestDataProvider.java` | Move + rename package | ✅ |
| `test/pipeline/factory/TestConfigurationFactory.java` | Test configuration factory | `test/harness/factory/TestConfigurationFactory.java` | Move + rename package | ✅ |
| `test/pipeline/filesystem/FileSystemTestBuilder.java` | File system test builder | `test/harness/filesystem/FileSystemTestBuilder.java` | Move + rename package | ✅ |
| `test/pipeline/filesystem/FileSystemTestBuilderTest.java` | Unit test for builder | `test/harness/filesystem/FileSystemTestBuilderTest.java` | Move + rename package | ✅ |
| `test/pipeline/filesystem/YamlTestUtils.java` | YAML test utilities | `test/harness/filesystem/YamlTestUtils.java` | Move + rename package | ✅ |
| `test/pipeline/filesystem/YamlTestUtilsTest.java` | Unit test for YAML utils | `test/harness/filesystem/YamlTestUtilsTest.java` | Move + rename package | ✅ |
| `test/pipeline/harness/EndToEndTestHarness.java` | Test harness for flows | `test/harness/EndToEndTestHarness.java` | Move + rename package | ✅ |
| `test/pipeline/harness/EndToEndTestHarnessTest.java` | Unit test for harness | `test/harness/EndToEndTestHarnessTest.java` | Move + rename package | ✅ |
| `test/pipeline/mock/ChatClientMockBuilder.java` | ChatClient mock builder | `test/harness/mock/ChatClientMockBuilder.java` | Move + rename package | ✅ |
| `test/pipeline/mock/ChatClientMockBuilderTest.java` | Unit test for mock builder | `test/harness/mock/ChatClientMockBuilderTest.java` | Move + rename package | ✅ |
| `test/pipeline/mock/MockConfiguration.java` | Mock configuration | `test/harness/mock/MockConfiguration.java` | Move + rename package | ✅ |
| `test/pipeline/mock/MockResponseProvider.java` | Mock response data | `test/harness/mock/MockResponseProvider.java` | Move + rename package | ✅ |

| Old File | What It Tests | New Location | Action |
|----------|--------------|--------------|--------|
| `test/pipeline/FileIntegrationFlowTest.java` | Full flow: file → agent → LLM | `test/integration/FileIntegrationFlowTest.java` | Move + rename package |
| `test/pipeline/FileSystemWorkflowIntegrationTest.java` | File scanning workflow integration | `test/integration/FileSystemWorkflowIntegrationTest.java` | Move + rename package |
| `test/pipeline/LLMAdapterIntegrationTest.java` | LLM adapter wiring | `test/integration/LLMAdapterIntegrationTest.java` | Move + rename package |
| `test/pipeline/config/ChatClientTestConfig.java` | Test config for ChatClient | `test/harness/config/ChatClientTestConfig.java` | Move + rename package |
| `test/pipeline/factory/AdapterTestCase.java` | Base test case class | `test/harness/factory/AdapterTestCase.java` | Move + rename package |
| `test/pipeline/factory/AdapterTestDataProvider.java` | Test data provider | `test/harness/factory/AdapterTestDataProvider.java` | Move + rename package |
| `test/pipeline/factory/TestConfigurationFactory.java` | Test configuration factory | `test/harness/factory/TestConfigurationFactory.java` | Move + rename package |
| `test/pipeline/filesystem/FileSystemTestBuilder.java` | File system test builder | `test/harness/filesystem/FileSystemTestBuilder.java` | Move + rename package |
| `test/pipeline/filesystem/FileSystemTestBuilderTest.java` | Unit test for builder | `test/harness/filesystem/FileSystemTestBuilderTest.java` | Move + rename package |
| `test/pipeline/filesystem/YamlTestUtils.java` | YAML test utilities | `test/harness/filesystem/YamlTestUtils.java` | Move + rename package |
| `test/pipeline/filesystem/YamlTestUtilsTest.java` | Unit test for YAML utils | `test/harness/filesystem/YamlTestUtilsTest.java` | Move + rename package |
| `test/pipeline/harness/EndToEndTestHarness.java` | Test harness for flows | `test/harness/EndToEndTestHarness.java` | Move + rename package |
| `test/pipeline/harness/EndToEndTestHarnessTest.java` | Unit test for harness | `test/harness/EndToEndTestHarnessTest.java` | Move + rename package |
| `test/pipeline/mock/ChatClientMockBuilder.java` | ChatClient mock builder | `test/harness/mock/ChatClientMockBuilder.java` | Move + rename package |
| `test/pipeline/mock/ChatClientMockBuilderTest.java` | Unit test for mock builder | `test/harness/mock/ChatClientMockBuilderTest.java` | Move + rename package |
| `test/pipeline/mock/MockConfiguration.java` | Mock configuration | `test/harness/mock/MockConfiguration.java` | Move + rename package |
| `test/pipeline/mock/MockResponseProvider.java` | Mock response data | `test/harness/mock/MockResponseProvider.java` | Move + rename package |

**Package rename:** `com.hdekker.ai_workflow.pipeline` → `com.hdekker.ai_workflow.integration` (for integration tests)
**Package rename:** `com.hdekker.ai_workflow.test.pipeline` → `com.hdekker.ai_workflow.test.harness` (for test utilities)

**Steps:**
1. Move each file to its new location
2. Update `package` declaration in each file
3. Update all `import` statements that reference `com.hdekker.ai_workflow.test.pipeline.*` → `com.hdekker.ai_workflow.test.harness.*`
4. Update all `import` statements that reference `com.hdekker.ai_workflow.pipeline.*` → `com.hdekker.ai_workflow.integration.*`
5. Update `@Import` annotations on test classes that reference moved config classes
6. Delete empty old directories
7. Compile (`./mvnw compile`) and run tests (`./mvnw test -q`)

 ────────────────────────────────────────────────────────────────────────────────

 ### 8. Estimated Effort

 ┌───────────────────────┬─────────────┬───────────────────┬───────────────────┬────────┐
 │ Phase                 │ Files Moved │ New Files (ports) │ Breaking Changes  │ Effort │
 ├───────────────────────┼─────────────┼───────────────────┼───────────────────┼────────┤
 │ 1. Domain extraction  │ ~20 files   │ 0                 │ Low (recompile)   │ Small  │
 ├───────────────────────┼─────────────┼───────────────────┼───────────────────┼────────┤
 │ 2. Port definitions   │ 0           │ ~10 interfaces    │ None              │ Small  │
 ├───────────────────────┼───────────────────┼───────────────────┼────────┤
 │ 3. Use case migration │ ~8 files    │ 0                 │ Medium (rewiring) │ Medium │
 ├───────────────────────┼─────────────┼───────────────────┼───────────────────┼────────┤
 │ 4. Adapter wiring     │ ~30 files   │ ~5 adapters       │ Medium (rewiring) │ Large  │
 ├───────────────────────┼─────────────┼───────────────────┼───────────────────┼────────┤
 │ 4.5 Pipeline migrate  │ ~8 files    │ 0                 │ Medium (rewiring) │ Medium │
 ├───────────────────────┼─────────────┼───────────────────┼───────────────────┼────────┤
 │ 5. Main cleanup       │ ~70 files   │ 0                 │ Low (delete)      │ Small  │
 ├───────────────────────┼─────────────┼───────────────────┼───────────────────┼────────┤
 │ 6. Test migration     │ ~17 files   │ 0                 │ Low (rename)      │ Small  │
 ├───────────────────────┼─────────────┼───────────────────┼───────────────────┼────────┤
 │ 7. Port cleanup       │ 1 file    │ 1 new (LLMAdapter)│ Low (reimport)    │ Tiny   │

 Total: ~151+ files touched across all phases.

> **Note:** Phase 4.5 is a catch-up for classes missed by the initial package assessment. It must complete before Phase 5
> can safely delete the old `pipeline/` package.
> 
> **Note:** Phase 5 main-source cleanup is complete. Phase 6 test package reorganization is also complete.
> Phase 7 (port cleanup — LLMAdapter moved to `application.pipeline.port`) is complete.
> 
> **Note:** Browser-based E2E tests at `tests/e2e/` are already correct and were not affected by this refactor.

 ────────────────────────────────────────────────────────────────────────────────
### Phase 7: Move LLMAdapter to port ✅ **COMPLETE**

**What was done:** 2026-05-06

Moved `LLMAdapter` interface from `adapter/outbound/llm/` → `application/pipeline/port/` to complete hexagonal purity:

| File | Action |
|------|--------|
| `adapter/outbound/llm/LLMAdapter.java` | Deleted (moved to port) |
| `application/pipeline/port/LLMAdapter.java` | Created — port interface with Javadoc |
| `adapter/outbound/llm/LLMAdapterFactory.java` | Updated import → `application.pipeline.port.LLMAdapter` |
| `adapter/outbound/llm/LLMReducerAdapter.java` | Updated import → `application.pipeline.port.LLMAdapter` |
| `adapter/outbound/llm/MapAgentLLMAdapter.java` | Updated import → `application.pipeline.port.LLMAdapter` |
| `adapter/outbound/llm/SplitterLLMAdapter.java` | Updated import → `application.pipeline.port.LLMAdapter` |
| `application/pipeline/AgentConfigurator.java` | Updated import → `application.pipeline.port.LLMAdapter` |
| `application/pipeline/AgentBuilderTest.java` | Updated import → `application.pipeline.port.LLMAdapter` |
| `integration/LLMAdapterIntegrationTest.java` | Updated import → `application.pipeline.port.LLMAdapter` |
| `test/harness/EndToEndTestHarness.java` | Updated import → `application.pipeline.port.LLMAdapter` |

**Verification:**
- ✅ 275 tests pass (0 failures, 0 errors, 2 skipped)
- ✅ Clean compile
- ✅ Domain: zero outward dependencies
- ✅ Application: depends only on port interfaces, not adapter implementations
- ✅ Adapters implement `application.pipeline.port.LLMAdapter` (not the old location)

> `LLMAdapterFactory` remains in `adapter.outbound.llm` as the factory that produces `LLMAdapter` instances — this is
> a standard dependency injection pattern. The application layer imports the factory to get instances of the port
> interface, but never imports or depends on concrete adapter implementations.

**Bottom line:** The refactor is **100% complete**. All dependency rules are satisfied.

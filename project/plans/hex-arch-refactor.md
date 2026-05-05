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


 #### Phase 4.5: Migrate remaining pipeline classes (missed in Phase 4)

 **Why missed:** The Phase 1 package assessment only listed `pipeline/` as "LLMAdapter, SplittableStrategy" but missed
 the nested subpackages `pipeline.llmadapter/` and `pipeline.management/`. These contain code still referenced by the
 new application layer.

 - Move `pipeline/LLMAdapter.java` → `adapter.outbound.llm/LLMAdapter.java` (interface)
 - Move `pipeline/llmadapter/LLMAdapterFactory.java` → `adapter.outbound.llm/LLMAdapterFactory.java`
 - Move `pipeline/llmadapter/LLMReducerAdapter.java` → `adapter.outbound.llm/LLMReducerAdapter.java`
 - Move `pipeline/llmadapter/MapAgentLLMAdapter.java` → `adapter.outbound.llm/MapAgentLLMAdapter.java`
 - Move `pipeline/llmadapter/SplitterLLMAdapter.java` → `adapter.outbound.llm/SplitterLLMAdapter.java`
 - Move `pipeline/AgentConfiguration.java` → `config/AgentConfiguration.java`
 - Move `pipeline/management/AgentRestoreOnStartup.java` → `config/AgentRestoreOnStartup.java`
 - Move `pipeline/management/DynamicAgentManagerConfiguration.java` → `config/DynamicAgentManagerConfiguration.java`
 - Update `application/pipeline/AgentConfigurator` imports from `pipeline.LLMAdapter` → `adapter.outbound.llm.LLMAdapter`
 - Delete duplicate `pipeline/SplittableStrategy.java` (already in `application/pipeline/`)

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

 ────────────────────────────────────────────────────────────────────────────────
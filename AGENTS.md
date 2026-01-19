# AGENTS.md


## Build Commands
These commands use the Maven wrapper (`./mvnw`).
The project expects JDK 21. Ensure `java -version` reports 21.x before running.

- `./mvnw clean install` – standard compile, test and package.
- `./mvnw verify -Pproduction` – runs production profile with frontend optimisations.
- `./mvnw spring-boot:run` – start development server.
- `./mvnw install -DskipTests` – build without executing tests.

## Test Commands
Run unit or integration tests via the wrapper:

- `./mvnw test` – all tests.
- `./mvnw test -Dtest=ClassName` – single test class.
- `./mvnw test -Dtest=ClassName#methodName` – specific test method.
- `./mvnw test -Dit.test=ClassName` – integration tests that are annotated with `@IntegrationTest`.

## Tooling & Environment

- **Maven Wrapper** – check existence of `mvnw`/`mvnw.cmd`. It guarantees the correct Maven version for the project.
- **Java Version** – the wrapper enforces JDK 21. If a different JDK is used, Maven will abort with a version mismatch error.
- **Environment Variables** – if you encounter memory issues, set `MAVEN_OPTS="-Xmx2g"` or higher.
- **Proxy / Artifact Repositories** – configure `~/.m2/settings.xml` if behind a corporate proxy.

## Lint & Formatting
Run the IDE formatter with 4‑space indentation and no tabs. No dedicated Maven‑based lint plugin is configured; verification is performed by running `./mvnw verify`.

## Code Style Guidelines
- Imports: Sorted alphabetically, static imports first
- Formatting: 4‑space indentation, braces on new line
- Line Length: Max 120 characters
- Types: Avoid raw types, prefer generics
- Naming: PascalCase classes, camelCase methods, UPPER_SNAKE_CASE constants
- Error Handling: Use Spring's @ControllerAdvice
- Logging: Always SLF4J with @Slf4j
- Annotations: @NonNull for parameters
- Testing: Test* naming, Mockito mocks, given/when/then structure
- Methods: Max 30 lines, avoid long methods
- Variables: Meaningful names, preferably final
- Database: Always use prepared statements
- Javadoc: Public methods must have Javadoc

## Contributor Guidelines
- Commit messages: 'feat:', 'fix:', 'docs:'
- Run `./mvnw verify` (lint + tests) before commit
- Do not commit secrets (check .gitignore)
- Follow Spring Boot, Vaadin, and project conventions
- When modifying UI, update corresponding Vaadin components
- All tests must pass before merge
- Refactor incrementally using TDD
- Avoid modifying existing test data structures
- Check for memory leaks in resource streams
- Prefer immutable collections where applicable

No Cursor/Copilot rules configured; use IDE default formatting.

## LLM Adapters

| Adapter | Type | Description |
|---------|------|-------------|
| **LLMAdapterFactory** | Factory | Creates appropriate LLMAdapter instances based on `AgentDefinition.agentType`. Supports "Reduction", "Split", and default MapAgent adapters. |
| **MapAgentLLMAdapter** | Adapter | Transforms each `PromptRequest` into a `PromptResponse` using the provided `Prompter`. Stateless and suitable for simple pipelines. |
| **ReducerLLMAdapter** | Adapter | Concatenates responses across a sequence of `PromptRequest` objects, maintaining state between calls. Useful for summarization or incremental context building. |
| **SplitterLLMAdapter** | Adapter | Parses LLM responses split by `--- ItemKey ---` tokens and emits multiple `PromptResponse` objects, each with the key incorporated into the filename for separate file outputs. Stateless and suitable for multi-file generation from single prompts. |

### SplitterLLMAdapter Architecture

**Purpose**: Enables the LLM to generate multiple distinct outputs from a single prompt, each saved as a separate file. Used for chain configurations with `agentType: Split`.

**Key Components**:
- **Input**: Single `PromptRequest` (file content and URL).
- **LLM Interaction**: Builds prompt with body, code, and output structure (similar to MapAgentLLMAdapter). Expects LLM to respond with content split by `--- ItemKey ---` tokens.
- **Response Parsing**: Uses regex `---\\s*(?<key>[^\\n]+)\\s*---` to extract key-content pairs. Each pair becomes a `PromptResponse`.
- **Output**: `Flux<PromptResponse>` with:
  - `response`: Content after the token (trimmed).
  - `fileName`: Modified to `${originalFileName}-${key}` for unique filenames.
  - `fileContents`: Original input content.
  - `prompt`: Shared `AgentDefinition`.
- **Edge Cases**:
  - No splits: Emits empty Flux (no responses).
  - Malformed splits: Skips invalid segments.
  - Streaming: Concatenates chunks before parsing.
- **Integration**: Activated via `LLMAdapterFactory` in `PromptPipelineConfigurator` when `agentType.equals("Split")`. Filename template uses regex groups from modified `fileName` for output paths.

**Design Decisions**:
- Hardcoded token `--- ItemKey ---` for simplicity.
- Stateless, one-to-many transformation.
- Filename modification ensures multiple outputs without changing `PromptResponse` structure.
- No error emission for invalid responses; silent failure for robustness.

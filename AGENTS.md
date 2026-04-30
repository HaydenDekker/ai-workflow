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

> **Prefer specific tests** — run targeted test classes or methods (`-Dtest=ClassName` or `-Dtest=ClassName#methodName`) for faster feedback. The full test suite (`./mvnw verify`) runs in the background for comprehensive coverage.

### Unit Tests
- `./mvnw test` – all unit tests with verbose output.
- `./mvnw test -q` – all unit tests with minimal output (recommended for LLM context).
- `./mvnw test -Dtest=ClassName -q` – single test class with minimal output.
- `./mvnw test -Dtest=ClassName#methodName -q` – specific test method with minimal output.

### Integration Tests (require external services like Ollama)
- `./mvnw verify -DskipTests` – run integration tests only.
- `./mvnw verify -DskipTests -q` – run integration tests with minimal output.
- `./mvnw verify -Dit.test=ClassName -q` – specific integration test with minimal output.

### All Tests
- `./mvnw verify` – run all tests (unit + integration).
- `./mvnw verify -q` – run all tests with minimal output.

> **Concise output for LLM context** — pipe through `grep` to extract only the summary lines:
> ```bash
> cd /c/Users/hayde/workspace_25/ai-workflow && ./mvnw test 2>&1 | grep -E "Tests run:|BUILD SUCCESS|BUILD FAILURE|Failures:" | tail -10
> ```
> This shows per-class results and the final BUILD status without the surrounding Maven noise.
>
> **⚠️ Do not combine `-q` with `grep` piping** — the `-q` (quiet) flag suppresses the Maven output that `grep` needs to filter. Use `-q` alone for minimal output, or pipe verbose output (no `-q`) through `grep` for summaries.

### E2E Tests (Playwright)
End-to-end tests for the Vaadin/Hilla UI using real Chromium:

- `npm run test:e2e` – run all E2E tests headless (auto-starts Spring Boot server).
- `npm run test:e2e:headed` – run with visible browser (for debugging).
- `npm run test:e2e:ui` – interactive Playwright UI mode.
- `npx playwright test tests/e2e/observability.spec.ts` – run a specific test file.

> **How it works:** Global setup starts `./mvnw spring-boot:run`, waits for the server, runs tests in Chromium, then stops the server. Takes screenshots, video, and traces on failure.

> **Prerequisites:** Node.js installed, `@playwright/test` added to `package.json`, Chromium browser installed via `npx playwright install chromium`.

### Screenshot Utility
Capture view screenshots automatically via a CLI utility that starts the dev server, navigates, renders, and saves:

```bash
# Basic usage – saves to project/screenshots/<slug>.png
npx tsx scripts/capture-snapshot.ts /agents
npx tsx scripts/capture-snapshot.ts /observability

# Custom wait time and output filename
npx tsx scripts/capture-snapshot.ts /agents --wait 5000 --output agents-custom.png

# Custom viewport (e.g. mobile)
npx tsx scripts/capture-snapshot.ts /observability --viewport 800x600
```

**Options:**
| Flag | Default | Description |
|------|---------|-------------|
| `<url-path>` | *(required)* | Route to capture (e.g. `/observability`) |
| `--output <name>` | `<slug>.png` | Custom output filename |
| `--wait <ms>` | `8000` | Wait time for view to render before capture |
| `--viewport <WxH>` | browser default | Custom viewport size (e.g. `1280x720`) |

**How it works:** Auto-starts Spring Boot, waits for readiness, navigates, captures to `project/screenshots/`, then stops the server.

> **Note:** `project/screenshots/` is gitignored – screenshots are local-only.

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
- When planning and making change, attempt non-breaking changes with tests in between each step before removing the existing code.
- Avoid modifying existing test data structures
- Check for memory leaks in resource streams
- Prefer immutable collections where applicable

No Cursor/Copilot rules configured; use IDE default formatting.

## Source References

When debugging framework-level issues, consult the local source repositories:

| Framework | Local Source Path | When to Consult |
|-----------|-------------------|------------------|
| **Storybook** | `C:\Users\hayde\workspace_26\libraries\storybook` | Storybook config errors, addon issues, v10 migration, `storybook/test` API, preview/manager internals |
| **Spring Boot** | `C:\Users\hayde\workspace_26\libraries\spring-boot` | Auto-configuration, `@SpringBootTest` behavior, web server lifecycle, property binding, bean resolution |
| **Vaadin / Hilla** | `C:\Users\hayde\workspace_26\libraries\vaadin\docs` | Vaadin components, UI rendering, routing, Hilla integration, browserless testing |

If you encounter errors or unexpected behavior in any of these frameworks, **search the local source** — the implementation details, migration guides, and test examples will be there.

## Document Management

The project tracks architectural decisions and design patterns through two durable document types:

- **ADRs** (Architecture Decision Records) — `project/adrs/adr-NNN-<slug>.md` — capture *why* a decision was made, alternatives considered, and consequences.
- **DPRs** (Design Pattern Records) — `project/docs/dpr-<slug>.md` — capture *how* a concept works, with implementation details, code examples, and tutorials.

Both are managed through the central index:

| Resource | Path | Purpose |
|----------|------|---------|
| Design Principles (master index) | `project/docs/design-principles.md` | Catalog of all ADRs and DPRs, naming conventions, cross-reference rules, ADR/DPR structure, and how to create new documents |

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

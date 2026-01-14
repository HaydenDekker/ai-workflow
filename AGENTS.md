	# AGENTS.md

## Build Commands
mvn clean install
mvn verify -Pproduction (production build with frontend optimizations)
mvn spring-boot:run (development server)
mvn install -DskipTests (skip tests during build)

## Test Commands
mvn test (run all tests)
mvn test -Dtest=ClassName (run single test class)
mvn test -Dtest=ClassName#methodName (run specific method)
mvn test -Dit.test=ClassName (run integration tests)

## Lint Commands
Run IDE formatter (4-space indentation, no tabs)
No Maven plugin configured; verify via IDE

## Code Style Guidelines
- Imports: Sorted alphabetically, static imports first
- Formatting: 4-space indentation, braces on new line
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
- Run `mvn verify` (lint + tests) before commit
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
| **MapAgentLLMAdapter** | Adapter | Transforms each `PromptRequest` into a `PromptResponse` using the provided `Prompter`. Stateless and suitable for simple pipelines. |
| **ReducerLLMAdapter** | Adapter | Concatenates responses across a sequence of `PromptRequest` objects, maintaining state between calls. Useful for summarization or incremental context building. |


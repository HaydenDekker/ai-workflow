# Step‑by‑Step Refactor Plan

This file documents the incremental changes required to replace all manual `ChatClient` mock creations with the unified `ChatClientMockBuilder`. Each step is a single, test‑able change.

> **How to use**
> 1. Apply the changes for **Step 1**.
> 2. Run `./mvnw test -q -Dtest=PromptPipelineConfiguratorTest`.
> 3. If the test passes, commit and move on to the next step.
> 4. Repeat until all steps are applied.

| # | File | Action | Test command |
|---|------|--------|--------------|
| 1 | `PromptPipelineConfiguratorTest.java` | Replace manual mock and stubbing with `ChatClientMockBuilder.forMapAdapter(expectedMockResult)`. | `./mvnw test -q -Dtest=PromptPipelineConfiguratorTest` |
| 2 | `SplitterLLMAdapterTest.java` | Swap in `ChatClientMockBuilder.forSplitterAdapter(responses)`. | `./mvnw test -q -Dtest=SplitterLLMAdapterTest` |
| 3 | `MapAgentLLMAdapterTest.java` | Use `ChatClientMockBuilder.forMapAdapter(responses)`. | `./mvnw test -q -Dtest=MapAgentLLMAdapterTest` |
| 4 | `LLMReducerAdapterTest.java` | Replace with `ChatClientMockBuilder.forReducerAdapter(accumulatedResponses)`. | `./mvnw test -q -Dtest=LLMReducerAdapterTest` |
| 5 | `PromptPipelineBuilderTest.java` | Switch to `ChatClientMockBuilder.forMapAdapter(...)`. | `./mvnw test -q -Dtest=PromptPipelineBuilderTest` |
| 6 | `DynamicPipelineManagerTest.java` | Use `ChatClientMockBuilder.forMapAdapter(...)`. | `./mvnw test -q -Dtest=DynamicPipelineManagerTest` |
| 7 | `EndToEndTestHarnessTest.java` | Update any mock usage to builder variants or remove if unnecessary. | `./mvnw test -q -Dtest=EndToEndTestHarnessTest` |
| 8 | `PipelineRestControllerTest.java` | Ensure all `ChatClient` mocks use `ChatClientMockBuilder`. | `./mvnw test -q -Dtest=PipelineRestControllerTest` |
| 9 | `ChatClientTestConfig.java` | Verify helper methods compile and imports are clean. | `./mvnw test -q -Dtest=ChatClientTestConfig` |
| 10 | Optional clean‑ups | Remove leftover `Mockito.mock`/`when` calls and unused imports. | Full test run: `./mvnw test` |

**Notes**
- When adjusting imports, remove `import org.mockito.*;` and add `import com.hdekker.ai_workflow.test.pipeline.mock.ChatClientMockBuilder;`.
- The mock builder exposes methods for all three adapter types; choose the one matching the test's original stub.
- If a test previously had custom configuration via `when(...).thenReturn(...)`, replicate that logic using the corresponding builder API.
- Maintain test readability and keep assertions unchanged.

Happy refactoring!
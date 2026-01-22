# ChatClient Mock Builder Refactor

## Goal
Replace all manual `ChatClient` mock creations in the test suite with the unified `ChatClientMockBuilder` utilities. This ensures consistent mock behaviour, simplifies test setup, and improves maintainability.

## Scope
- **All test files** under `src/test/java` that currently use `Mockito.mock(ChatClient.class)` or stub `ChatClient.prompt(...)
`.
- Use the helper methods provided by `ChatClientTestConfig` (`createMapAdapterMock`, `createSplitterAdapterMock`, `createReducerAdapterMock`, `withConfiguration`).

## Steps
1. **Search** for `mock(ChatClient.class)` and `Mockito.mock(ChatClient.class)` in the test tree. Confirm all occurrences.
2. **Update each file**:
   - Replace manual mock object creation with a call to the appropriate helper method.
   - Remove unused imports (`org.mockito.Mockito`, static imports for `when`) and any other now‑unused code.
   - Adjust test logic if necessary for the builder to produce the same responses.
3. **Run tests** (`./mvnw test`) to ensure all tests still pass.
4. **Commit** with a clear message: `refactor: replace manual ChatClient mocks with ChatClientMockBuilder across tests`.

## Files to Update
| File | Current Pattern | Replacement |
|------|-----------------|-------------|
| `PromptPipelineConfiguratorTest.java` | Manual `ChatClient`, `ChatClientRequestSpec`, `StreamResponseSpec` + stubbing | `ChatClientTestConfig.createMapAdapterMock(expectedMockResult)` |
| `SplitterLLMAdapterTest.java` | Manual mock | `ChatClientMockBuilder.forSplitterAdapter(...)` |
| `MapAgentLLMAdapterTest.java` | Manual mock | `ChatClientMockBuilder.forMapAdapter(...)` |
| `LLMReducerAdapterTest.java` | Manual mock | `ChatClientMockBuilder.forReducerAdapter(...)` |
| `PromptPipelineBuilderTest.java` | Manual mock | `ChatClientMockBuilder.forMapAdapter(...)` |
| `DynamicPipelineManagerTest.java` | Manual mock | `ChatClientMockBuilder.forMapAdapter(...)` |
| *Any additional files* containing `mock(ChatClient.class)` | Follow same pattern |

## Validation
- Verify no remaining `mock(ChatClient.class)` usage.
- All tests pass.
- Code style and import cleanup performed.


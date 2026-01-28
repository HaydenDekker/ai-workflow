# FileSystemWorkflowIntegrationTest Refactoring Plan

This document tracks the progress of simplifying the FileSystemWorkflowIntegrationTest to focus only on core file system workflow validation with a single adapter.

## Todo List

- [x] **1. Create simplified test structure with single MapAgent**
  - Remove all Splitter and Reducer adapter test cases
  - Keep only basic MapAgent scenario
  - Simplify test configuration to use single adapter type

- [x] **2. Remove all Splitter and Reducer adapter logic**
  - Delete all test cases for "Split" and "Reduction" adapter types
  - Remove corresponding mock response providers
  - Eliminate adapter type checks in test logic

- [x] **3. Eliminate EndToEndTestCase and EndToEndExpectedResults records**
  - Remove the EndToEndTestCase record class (lines 241-248)
  - Remove the EndToEndExpectedResults record class (lines 253-273)
  - Replace with direct parameter passing in test method

- [x] **4. Replace parameterized test with simple test method**
  - Remove `@ParameterizedTest` and `@MethodSource`
  - Replace with simple `@Test` method
  - Remove stream generation for test cases

- [x] **5. Remove all adapter-specific verification code**
  - Delete entire `verifyAdapterSpecificResults()` method (lines 193-236)
  - Remove all conditional logic based on adapter type
  - Keep only basic assertions for response and file counts

- [x] **6. Cleanup unused imports and dependencies**
  - Remove unused imports from removed components
  - Remove any remaining references to: EndToEndTestCase, EndToEndExpectedResults
  - Remove unused test harness dependencies if no longer needed

- [x] **7. Verify file creation and adapter call tracking**
  - Ensure `chatClientTestConfig.createMock()` is called and verified
  - Confirm files are created in output directory
  - Verify response count is 1 for MapAgent
  - Validate that file count matches response count (1:1)

- [x] **8. Run tests to ensure simplified version works**
  - Execute `./mvnw test -Dtest=FileSystemWorkflowIntegrationTest -q`
  - Validate all assertions pass
  - Confirm no regressions in file system workflow

## Progress Notes

This refactoring will transform the test from a comprehensive multi-adapter integration test to a focused test that validates:
1. File events are received from the file system scanner
2. The adapter is called exactly once with the correct input
3. Files are created in the output directory based on adapter responses

The simplified test will maintain test integrity while removing unnecessary complexity related to other adapter types, which are covered in their own dedicated tests.
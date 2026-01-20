package com.hdekker.ai_workflow.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.hdekker.ai_workflow.TestFiles;
import com.hdekker.ai_workflow.TestProfiles;
import com.hdekker.ai_workflow.app.pipeline.PromptPipelineBuilder;
import com.hdekker.ai_workflow.files.FileSystemScannerConfig;
import org.springframework.ai.chat.client.ChatClient;
import com.hdekker.ai_workflow.pipeline.domain.AgentDefinition;
import com.hdekker.ai_workflow.pipeline.llmadapter.LLMReducerAdapter;
import com.hdekker.ai_workflow.pipeline.support.AdapterTestCase;
import com.hdekker.ai_workflow.pipeline.support.MockResponseProvider;
import com.hdekker.ai_workflow.pipeline.support.TestConfigurationFactory;
import com.hdekker.ai_workflow.prompt.PromptRequest;
import com.hdekker.ai_workflow.prompt.PromptResponse;

import reactor.core.publisher.Flux;

/**
 * Refactored WorkflowIntegrationTest using parameterized tests for all LLM adapter types.
 * 
 * This test class verifies document creation behavior for:
 * - MapAgentLLMAdapter: 1:1 input to output transformation
 * - SplitterLLMAdapter: 1:N transformation using --- ItemKey --- tokens  
 * - ReducerLLMAdapter: N:N transformation with state accumulation
 * - Default Map fallback: When agentType is null or unspecified
 * 
 * Uses programmatic configuration stubs instead of filesystem-based configuration
 * for better test isolation and maintainability.
 * 
 * Each test case includes:
 * - AgentDefinition configuration specific to the adapter type
 * - Mock LLM response tailored to test adapter behavior
 * - Expected document count verification
 * - Adapter-specific behavior validation
 */
@SpringBootTest()
@ActiveProfiles({
	TestProfiles.RESOURCES_TEST_FOLDER,
	TestProfiles.FIXED_LLM_TEST_RESPONSE})
public class WorkflowIntegrationTest {
	
	Logger log = LoggerFactory.getLogger(WorkflowIntegrationTest.class);
	
	@Autowired
	FileSystemScannerConfig fileSystemScannerConfig;
	
	@Autowired
	PromptPipelineTestConfig config;
	
	Path configuredDirectory;
	
	@Autowired
	TestFiles testFiles;
	
	@TempDir 
	static Path promptDirectory;
	
	@TempDir 
	static Path rootDirectory;
	
	@DynamicPropertySource 
    static void registerTempDirProperty(DynamicPropertyRegistry registry) {
        registry.add("prompt-config.predefinedPromptFilePath", () -> promptDirectory.toAbsolutePath().toString());
        registry.add("scanner.url", () -> "file:/" + rootDirectory.toAbsolutePath().toString());
    }
	
	@BeforeEach
	public void captureConfiguration() {
		try {
			configuredDirectory = Paths.get(fileSystemScannerConfig.getUrl().getURI());
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Provides test cases for all LLM adapter types.
	 * Each case includes configuration, mock response, and expected behavior.
	 */
	static Stream<AdapterTestCase> adapterTestCases() {
		return Stream.of(
			// Map Agent Test Case
			new AdapterTestCase(
				"Map",
				TestConfigurationFactory.createMapAgentDefinition(),
				MockResponseProvider.getMapAgentResponse(),
				1, // inputCount
				1, // expectedOutputCount
				"MapAgent 1:1 transformation",
				null // no split keys for Map agent
			),
			
			// Split Agent Test Case
			new AdapterTestCase(
				"Split",
				TestConfigurationFactory.createSplitterAgentDefinition(),
				MockResponseProvider.getSplitterResponse(),
				1, // inputCount
				3, // expectedOutputCount (3 splits in mock response)
				"Splitter 1:N transformation",
				MockResponseProvider.getSplitterKeys()
			),
			
			// Reducer Agent Test Case
			new AdapterTestCase(
				"Reduction",
				TestConfigurationFactory.createReducerAgentDefinition(),
				MockResponseProvider.getReducerInitialResponse(),
				2, // inputCount
				2, // expectedOutputCount (1:1 but with state accumulation)
				"Reducer stateful transformation",
				null // no split keys for Reducer agent
			),
			
			// Default Map Agent Test Case (null agentType)
			new AdapterTestCase(
				null, // null agentType should default to Map
				TestConfigurationFactory.createDefaultMapAgentDefinition(),
				MockResponseProvider.getDefaultMapResponse(),
				1, // inputCount
				1, // expectedOutputCount
				"Default Map agent fallback",
				null // no split keys for Default Map agent
			)
		);
	}
	
	
	/**
	 * Parameterized test for all LLM adapter types.
	 * Verifies that each adapter creates the expected number of documents
	 * and handles input/output transformation correctly.
	 * 
	 * @param testCase The adapter test case with configuration and expectations
	 * @throws InterruptedException if thread sleep is interrupted
	 * @throws IOException if file operations fail
	 */
	@ParameterizedTest
	@MethodSource("adapterTestCases")
	public void givenAdapterType_ExpectCorrectDocumentCreation(AdapterTestCase testCase) 
			throws InterruptedException, IOException {
		
		log.info("Testing adapter type: {}", testCase.adapterType());
		
	// Reset mock configuration for this test
		config.resetResponses();
		
		// Set appropriate mock responses based on adapter type
		if (testCase.isReducerAdapter()) {
			config.setMockResponses(MockResponseProvider.getReducerResponses());
		} else {
			config.setMockResponse(testCase.mockResponse());
		}
		
		// Create test input data based on adapter requirements
		List<PromptRequest> inputs = createTestInputs(testCase);
		
		// Build the pipeline with the test configuration
		Flux<PromptResponse> pipeline = PromptPipelineBuilder.instance()
			.withDefinition(testCase.agentDefinition())
			.withTrigger(Flux.fromIterable(inputs))
			.prompting(pr -> {
				// The adapter will be selected based on agentType in the definition
				return com.hdekker.ai_workflow.pipeline.llmadapter.LLMAdapterFactory
					.create(config.chatClient(), testCase.agentDefinition())
					.call(pr);
			})
			.persist(l -> log.info("Persisting response: {}", l))
			.split(com.hdekker.ai_workflow.pipeline.SplittableStrategy.noSPLT())
			.build();
		
		// Execute the pipeline and collect results
		List<PromptResponse> responses = pipeline.collectList().block();
		
		// Verify the mock was called
		assertThat(config.prompterCalled)
			.isTrue();
		
		// Verify document creation count matches expectations
		assertThat(responses)
			.hasSize(testCase.expectedOutputCount());
		
		// Additional adapter-specific verification
		verifyAdapterSpecificBehavior(testCase, responses, inputs);
		
		log.info("Successfully verified {} adapter with {} outputs", 
			testCase.testName(), responses.size());
	}
	
	/**
	 * Creates test input data based on the adapter test case requirements.
	 */
    private List<PromptRequest> createTestInputs(AdapterTestCase testCase) {
        if (testCase.isReducerAdapter()) {
            // Reducer needs multiple inputs to test state accumulation
            return Arrays.asList(
                new PromptRequest("First function analysis", "test/function1.md"),
                new PromptRequest("Second function analysis", "test/function2.md")
            );
        } else {
            // Map and Split adapters work with single input
            return Arrays.asList(
                new PromptRequest("Test input content", "test/TestFile.java")
            );
        }
    }
	
	/**
	 * Performs adapter-specific verification beyond document count.
	 */
	private void verifyAdapterSpecificBehavior(AdapterTestCase testCase, 
			List<PromptResponse> responses, List<PromptRequest> inputs) {
		
		if (testCase.isSplitAdapter()) {
			// Verify Split adapter creates files with correct key suffixes
			assertThat(responses)
				.extracting(r -> r.fileName())
				.allMatch(filename -> {
					// Check that filename contains one of the expected split keys
					return Arrays.stream(testCase.expectedFileKeys())
						.anyMatch(key -> filename.contains("-" + key));
				});
				
			// Verify each response has content
			assertThat(responses)
				.allMatch(r -> r.response() != null && !r.response().trim().isEmpty());
				
		} else if (testCase.isReducerAdapter()) {
			// Verify Reducer maintains state across inputs
			assertThat(responses)
				.hasSize(inputs.size());
				
			// Verify responses accumulate state (content should grow)
			for (int i = 1; i < responses.size(); i++) {
				assertThat(responses.get(i).response().length())
					.isGreaterThan(responses.get(i-1).response().length());
			}
			
		} else if (testCase.isMapAdapter()) {
			// Verify Map adapter maintains 1:1 correspondence
			assertThat(responses)
				.hasSameSizeAs(inputs);
				
			// Verify file URLs match input patterns
			for (int i = 0; i < inputs.size(); i++) {
				assertThat(responses.get(i).fileName())
					.isNotEmpty();
			}
		}
	}
	
	/**
	 * Legacy test maintained for backward compatibility.
	 * Tests the ReducerLLMAdapter directly using the old approach.
	 * TODO: This can be removed once parameterized tests are fully validated.
	 */
	@Autowired
	ChatClient chatClient;
	
	// TODO move this to builder test or lower as a LLMAdapter test. The adapter has to get
	// the latest file before proceeding, potentially a factory method.
	@ParameterizedTest
	@MethodSource("reducerTestCases")
	public void givenPromptChainWithReduceAdapterSet_ExpectOutputResultsFromThePipeline(AdapterTestCase testCase) {
		
		// Set up the mock response for this test
		config.resetResponses();
		config.setMockResponses(Arrays.asList(
			MockResponseProvider.getReducerInitialResponse(),
			MockResponseProvider.getReducerAccumulatedResponse()
		));
		
		String inputOne = "This is a test input";
		String inputTwo = "Another test input";
		
		LLMReducerAdapter llmReducerAdapter = new LLMReducerAdapter(chatClient, testCase.agentDefinition());
		
		Flux<PromptResponse> pipeline = PromptPipelineBuilder.instance()
			.withDefinition(testCase.agentDefinition())
			.withTrigger(Flux.just(inputOne, inputTwo)
					.map(s->{
						return new PromptRequest(s, "some/url");
					}))
			.prompting(llmReducerAdapter::call)
			.persist(l-> log.info("persisting " + l))
			.split(com.hdekker.ai_workflow.pipeline.SplittableStrategy.noSPLT())
			.build();
		
		List<PromptResponse> reduced = pipeline.collectList()
			.block();
		
		assertThat(reduced.size())
			.isEqualTo(testCase.expectedOutputCount());
			
		// Verify state accumulation in reducer
		assertThat(reduced.get(1).response().length())
			.isGreaterThan(reduced.get(0).response().length());
	}
	
	/**
	 * Test cases specifically for the legacy reducer test.
	 */
	static Stream<AdapterTestCase> reducerTestCases() {
		return Stream.of(
			new AdapterTestCase(
				"Reduction",
				TestConfigurationFactory.createReducerAgentDefinition(),
				MockResponseProvider.getReducerInitialResponse(),
				2, // inputCount
				2, // expectedOutputCount
				"Legacy Reducer Test",
				null
			)
		);
	}
	

}

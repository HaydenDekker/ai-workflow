package com.hdekker.ai_workflow.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
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
import com.hdekker.ai_workflow.llm.Prompter;
import com.hdekker.ai_workflow.pipeline.domain.PipelinePrompt;
import com.hdekker.ai_workflow.prompt.PromptRequest;
import com.hdekker.ai_workflow.prompt.PromptResponse;
import com.hdekker.ai_workflow.reports.ReportRestController;

import reactor.core.publisher.Flux;

/**
 * Pre-written prompt chains can target any 
 * information source. Useful to provide guidance
 * while allowing the user to take the lead, i.e a sidekick.
 * 
 */
@SpringBootTest
@ActiveProfiles({
	TestProfiles.RESOURCES_TEST_FOLDER,
	TestProfiles.FIXED_LLM_TEST_RESPONSE})
public class PromptPipelineTest {
	
	Logger log = LoggerFactory.getLogger(PromptPipelineTest.class);
	
	@Autowired
	FileSystemScannerConfig fileSystemScannerConfig;
	
	@Autowired
	ReportRestController reportRestController;
	
	public static final String TEXT_IN_SOLID_PRIORITY_PROMPT = "Priority of SOLID Non-Compliance Rectification";
	
	File configuredDirectory;
	
	@Autowired
	TestFiles testFiles;
	
	@TempDir 
	static Path promptDirectory;
	
	@TempDir 
	static Path rootDirectory;
	
	// TODO - Replace all directory access for testing with temp directory instances
	// including prompt directory and root directory
	@DynamicPropertySource 
    static void registerTempDirProperty(DynamicPropertyRegistry registry) {
        registry.add("prompt-config.predefinedPromptFilePath", () -> promptDirectory.toAbsolutePath().toString());
        registry.add("scanner.url", () -> "file:/" + rootDirectory.toAbsolutePath().toString());
    }
	
	@BeforeEach
	public void captureConfiguration() {
		try {
			configuredDirectory = fileSystemScannerConfig.getUrl().getFile();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	
	/**
	 * 
	 * dyn config
	 *  trigger - prompt title - output structure - output always a list
	 *  
	 *  triggers:
	 *   file change event
	 *   file replay event
	 *   prompt output event
	 * 
	 * @throws InterruptedException
	 * @throws IOException
	 */

	@Test
	public void givenSingleFileAndTwoPrompts_ExpectBothOutcomesStoredInDatabase() throws InterruptedException, IOException {
		
		testFiles.copyTestFileAnAllowToPropagte(TestFiles.FILE_POOR_SOLID_COMPLIANCE);
		
		// TODO name taken from current config. Need to create test config.
		List<PromptResponse> reportItems = reportRestController.resultsForPrompt("SOLID_NON_COMPLIANCE")
				.collectList()
				.block();

		// TODO make explicit - split after save.
		assertThat(reportItems)
			.hasSize(1);
		
		assertThat(reportItems.get(0).fileName())
			.isNotNull();
		
		List<PromptResponse> results = reportRestController.resultsForPrompt("PRIORITY_ORDER")
				.collectList()
				.block();
		
		// TODO make explicit - split above outputs 2 items.
		assertThat(results.size())
			.isEqualTo(2);
		
		// TODO text taken from output
		assertThat(results.get(0).prompt().body())
			.contains(TEXT_IN_SOLID_PRIORITY_PROMPT);
		
		Path outputFilePath = Paths.get(configuredDirectory.getPath() + "/" + reportItems.get(0).createOutputFileName());
		
		log.info("Checking file exists, " + outputFilePath.toString() );
		
		assertThat(Files.exists(outputFilePath))
			.isTrue();
		
	}
	
	@Autowired
	Prompter prompter;
	
	@Test
	public void givenPromptChainWithReduceAdapterSet_ExpectOutputResultsFromThePipeline() {
		
		String inputOne = "This is a test input";
		String inputTwo = "Another test input";
		String dummyEvent = "TEST_EVENT";
		String title = "Prompt Event Stage Test";
		
		PipelinePrompt pipelinePrompt = new PipelinePrompt(
				dummyEvent,
				"",
				title, 
				"Reduction not used",
				"Accumulate this new input into the previous response.",
				"List the items in the response.",
				"");
		
		LLMReducerAdapter llmReducerAdapter = new LLMReducerAdapter(prompter, pipelinePrompt);
		
		Flux<PromptResponse> pipeline = PromptPipelineBuilder.<PromptRequest, PromptResponse>instance()
			.withTrigger(Flux.just(inputOne, inputTwo)
					.map(s->{
						return new PromptRequest(s, "some/url");
					}))
			.prompting(llmReducerAdapter::call)
			.persist(l-> log.info("persisting " + l))
			.split(SplittableStrategy.noSPLT())
			.build();
		
		List<PromptResponse> reduced = pipeline.collectList()
			.block();
		
		assertThat(reduced.size())
			.isEqualTo(2);
		
	}
	

}

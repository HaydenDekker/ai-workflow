package com.hdekker.ai_workflow.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.hdekker.ai_workflow.TestFiles;
import com.hdekker.ai_workflow.TestProfiles;
import com.hdekker.ai_workflow.files.FileSystemScannerConfig;
import com.hdekker.ai_workflow.pipeline.domain.PipelinePrompt;
import com.hdekker.ai_workflow.pipeline.domain.PromptChain;
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
	
	public static final String TEST_FILES_DIR = "src/test/resources/test-files-init/";

	@Autowired
	FileSystemScannerConfig fileSystemScannerConfig;
	
	@Autowired
	ReportRestController reportRestController;
	
	public static final String TEXT_IN_SOLID_PRIORITY_PROMPT = "Priority of SOLID Non-Compliance Rectification";
	
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

	// TODO make clear - Integration test
	@Test
	public void givenSingleFileAndTwoPrompts_ExpectBothOutcomesStoredInDatabase() throws InterruptedException, IOException {
		
		File configuredDirectory = fileSystemScannerConfig.getUrl().getFile();
		Path destination = configuredDirectory.toPath().resolve(TestFiles.POOR_SOLID_COMPLIANCE);
		Path source = Paths.get(TEST_FILES_DIR + TestFiles.POOR_SOLID_COMPLIANCE);
		Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
		
		Thread.sleep(2000);
		
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
		
	}
	
	@Test
	public void givenPromptChainWithReduceFlagSet_ExpectOutputUsedForNextInput() {
		
		String inputOne = "This is a test input";
		String inputTwo = "Another test input";
		String dummyEvent = "TEST_EVENT";
		String title = "Reduce Prompt Stage Test";
		
		PipelinePrompt pipelinePrompt = new PipelinePrompt(
				dummyEvent, 
				title, 
				"Accumulate this new input into the previous response.",
				"List the items in the response.");
		
		PromptChain reducePromptChain = new PromptChain(List.of(pipelinePrompt));
		
		// TODO may be able move to to adapter being closer to the function.
		
//		PromptPipelineBuilder.<String, PromptResponse>instance()
//			.withTrigger(Flux.just(inputOne, inputTwo))
//			.prompting(fs-> fs.map(null))
		
	}
	

}

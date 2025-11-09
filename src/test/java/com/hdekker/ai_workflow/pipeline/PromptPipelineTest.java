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
import com.hdekker.ai_workflow.prompt.PromptConfiguration;
import com.hdekker.ai_workflow.reports.ReportRestController;

import reactor.core.publisher.Flux;

/**
 * Pre-written prompt chains can target any 
 * information source. Useful to provide guidance
 * while allowing the user to take the lead, i.e a sidekick.
 * 
 */
@SpringBootTest
@ActiveProfiles(TestProfiles.RESOURCES_TEST_FOLDER)
public class PromptPipelineTest {
	
	Logger log = LoggerFactory.getLogger(PromptPipelineTest.class);
	
	public static final String TEST_FILES_DIR = "src/test/resources/test-files-init/";

	@Autowired
	FileSystemScannerConfig fileSystemScannerConfig;
	
	@Autowired
	ReportRestController reportRestController;
	
	@Autowired
	PromptPipelineTestConfig testConfig;
	
	@Autowired
	PromptPipelineConfiguration promptPipelineConfiguration;
	
	
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
		
		List<PipelinePrompt> promptPipeline = testConfig.testPipelinePromptList();
		
		Flux<PromptResponse> pipeline = promptPipelineConfiguration.build(promptPipeline);
		pipeline.subscribe(s-> log.info(s.toString()));
		
		File configuredDirectory = fileSystemScannerConfig.getUrl().getFile();
		Path destination = configuredDirectory.toPath().resolve(TestFiles.POOR_SOLID_COMPLIANCE);
		Path source = Paths.get(TEST_FILES_DIR + TestFiles.POOR_SOLID_COMPLIANCE);
		Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
		
		Thread.sleep(2000);
		
		List<PromptResponse> reportItems = reportRestController.resultsForPrompt(PromptConfiguration.SOLID_COMPLIANCE_PROMPT_TITLE)
				.collectList()
				.block();

		assertThat(reportItems)
			.hasSizeGreaterThan(0);
		
		List<PromptResponse> results = reportRestController.resultsForPrompt(PromptConfiguration.PRIORITY_ORDER_PROMPT_TITLE)
				.collectList()
				.block();
		
		assertThat(results)
			.hasSizeGreaterThan(0);
		
	}
	

}

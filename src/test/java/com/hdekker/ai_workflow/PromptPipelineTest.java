package com.hdekker.ai_workflow;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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

import com.hdekker.ai_workflow.files.FileSystemScannerConfig;
import com.hdekker.ai_workflow.llm.output.SOLIDCompliance;
import com.hdekker.ai_workflow.reports.ReportRestController;

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
	
	@Test
	public void givenNewFileAndPrompt_ExpectLLMOutputStoredInDatabase() throws IOException, InterruptedException {
		
		File directory = fileSystemScannerConfig.getUrl().getFile();
		Path destination = directory.toPath().resolve(TestFiles.POOR_SOLID_COMPLIANCE);
		Path source = Paths.get(TEST_FILES_DIR + TestFiles.POOR_SOLID_COMPLIANCE);
		Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
		
		Thread.sleep(2000);
		
		List<SOLIDCompliance> reportItems = reportRestController.complianceReport()
				.collectList()
				.block();
		
		assertThat(reportItems)
			.hasSizeGreaterThan(0);
		
		
	}
	

}

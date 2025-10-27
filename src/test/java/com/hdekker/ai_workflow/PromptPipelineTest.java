package com.hdekker.ai_workflow;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
	

	public static final String TEST_FILES_DIR = "src/test/resources/test-files";
	
	String testFile = """
				package com.hdekker.ai_workflow.llm;

import org.springframework.beans.factory.annotation.Autowired;

public class SOLIDPromtCaller {
	
	@Autowired
	OllamaWorld ollamaWorld;
	
	//String prompt = "Given the code, provide a short summary of each function, only if there is an implemeneted function. It must be human readable and as high level as possible.";
	String prompt = 
			\"""
			Given the code below, ensure that it complies to SOLID principals. 
			You might not have external context for some classes so the assessment should just reason about the immediate code. 
			If it doesn't immediately break principals state that it is compliant.
			\n\r ---------- \n\r 
			\""";
	//String conciseOutput = "The output must be staight to the point. No wordy sentences, just specifc next task. eg. Implement this... Refactor this... and a sentence as to why it should be done.";
	//String conciseOutput = "The output must be a list of json objects with schema, {\"className\":String, \"function\": String, \"description\": String";
	String conciseOutput = "Output json, as {className:String, compliance: YES|NO, principle:String, task:String, reason:String}";
	
	public String prompt(String fileBody) {
		
		String aiResult = ollamaWorld.call(prompt + "\n\r\n\r" + conciseOutput + fileBody)
				.collectList()
				.block()
				.stream()
				.reduce((a,b)-> a+b)
				.orElse("");
		
		return aiResult;
	}

			}

			""";
	
	@Autowired
	FileSystemScannerConfig fileSystemScannerConfig;
	
	@Autowired
	ReportRestController reportRestController;
	
	@Test
	public void givenNewFileAndPrompt_ExpectLLMOutputStoredInDatabase() throws IOException, InterruptedException {
		
		File directory = fileSystemScannerConfig.getUrl().getFile();
		Path path = directory.toPath();
		Path filePath = path.resolve("test-file.java");
		Files.write(filePath, testFile.getBytes(StandardCharsets.UTF_8));
		
		Thread.sleep(2000);
		
		List<SOLIDCompliance> reportItems = reportRestController.complianceReport()
				.collectList()
				.block();
		
		assertThat(reportItems)
			.hasSizeGreaterThan(0);
		
		
	}
	

}

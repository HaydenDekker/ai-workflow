package com.hdekker.ai_workflow;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.hdekker.ai_workflow.database.FileMetaRepository;
import com.hdekker.ai_workflow.files.FileSystemScannerConfig;

import reactor.core.publisher.Flux;

@SpringBootTest
@ActiveProfiles(TestProfiles.RESOURCES_TEST_FOLDER)
public class PromptRunnerTest {
	
	Logger log = LoggerFactory.getLogger(PromptRunnerTest.class);
	
	@Autowired
	FileMetaRepository fileMetaRepository;
	
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
	
	@Test
	public void givenNewFileAndPrompt_ExpectLLMOutputStoredInDatabase() throws IOException {
		
		log.info("adding file");
		File directory = fileSystemScannerConfig.getUrl().getFile();
		Path path = directory.toPath();
		Path filePath = path.resolve("test-file.java");
		Files.write(filePath, testFile.getBytes(StandardCharsets.UTF_8));
		
		Flux.interval(Duration.ofSeconds(1))
			.filter(l-> {
				Integer size = fileMetaRepository.findAll().size();
				log.info("" + size);
				return size>0;
			})
			.timeout(Duration.ofSeconds(20))
			.blockFirst();
		
	}
	

}

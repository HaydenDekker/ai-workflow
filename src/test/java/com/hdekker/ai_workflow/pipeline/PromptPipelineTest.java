package com.hdekker.ai_workflow.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

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

import reactor.core.publisher.Flux;

/**
 * Pre-written prompt chains can target any 
 * information source. Useful to provide guidance
 * while allowing the user to take the lead, i.e a sidekick.
 * 
 * TODO rename to workflow integration test
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
	PromptPipelineTestConfig config;
	
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
	public void givenSingleFileAndTwoEdgePromptChain_ExpectTwoOutputsStoredInFilesystem() throws InterruptedException, IOException {
		
		testFiles.copyTestFileAnAllowToPropagte(TestFiles.FILE_POOR_SOLID_COMPLIANCE);
		
		assertThat(config.prompterCalled)
			.isTrue();
		
		Path outputFilePath = Paths.get(configuredDirectory.getPath() + "/" + "output/solid-priorty/non-compliance/" + "SOLIDPromptCaller" + ".md");
		
		log.info("Checking file exists, " + outputFilePath.toString() );
		
		assertThat(Files.exists(outputFilePath))
			.isTrue();
		
		Thread.sleep(2000);
		
		Path secondPromptFilePath = Paths.get(configuredDirectory.getPath() + "/" + "output/solid-priorty/priorty-order/" + "SOLIDPromptCaller" + ".md");
	
		try (Stream<Path> stream = Files.walk(Paths.get(configuredDirectory.getPath()))) {
		    stream.filter(Files::isRegularFile)
		          .forEach(p -> log.info("Found file: " + p.getFileName()));
		} catch (IOException e) {
		    e.printStackTrace();
		}
		
		assertThat(Files.exists(secondPromptFilePath))
			.isTrue();
		
	}
	
	@Autowired
	Prompter prompter;
	
	// TODO move this to builder test or lower as a LLMAdapter test. The adapter has to get
	// the latest file before proceeding, potentially a factory method.
	@Test
	public void givenPromptChainWithReduceAdapterSet_ExpectOutputResultsFromThePipeline() {
		
		String inputOne = "This is a test input";
		String inputTwo = "Another test input";
		String title = "Prompt Event Stage Test";
		
		PipelinePrompt pipelinePrompt = new PipelinePrompt(
				"",
				title, 
				"Reduction not used",
				"Accumulate this new input into the previous response.",
				"List the items in the response.",
				"");
		
		LLMReducerAdapter llmReducerAdapter = new LLMReducerAdapter(prompter, pipelinePrompt);
		
		Flux<PromptResponse> pipeline = PromptPipelineBuilder.instance()
			.withDefinition(pipelinePrompt)
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

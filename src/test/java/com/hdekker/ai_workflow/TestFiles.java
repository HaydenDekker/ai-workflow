package com.hdekker.ai_workflow;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

import com.hdekker.ai_workflow.files.FileSystemRecursiveFileScannerAdapter;
import com.hdekker.ai_workflow.files.FileSystemScannerConfig;
import com.vaadin.copilot.shaded.reactor.core.publisher.Flux;

import reactor.test.StepVerifier;

@Configuration
public class TestFiles {
	
	Logger log = LoggerFactory.getLogger(TestFiles.class);
	
	public static final String TEST_FILES_DIR = "src/test/resources/test-files-init/";
	public static final String FILE_POOR_SOLID_COMPLIANCE = "SOLIDPromptCaller.java";
	public static final String FILE_SOLID_NON_COMPLIANCE_OUTPUT_DIR = "SOLID_NON_COMPLIANCE";
	public static final String TYPICAL_RESPONSE_MD = "typical_response.md";
	
	public static String getTestFilePath(String filename) {
		return TEST_FILES_DIR + filename;
	}
	
	@Autowired
	FileSystemScannerConfig fileSystemScannerConfig;
	
	@Autowired
	FileSystemRecursiveFileScannerAdapter scannerAdapter;
	
	File configuredDirectory;
	
	TestFiles(FileSystemScannerConfig fileSystemScannerConfig){
		this.fileSystemScannerConfig = fileSystemScannerConfig;
		try {
			configuredDirectory = fileSystemScannerConfig.getUrl().getFile();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	
	void copyTestFileToConfiguredMonitoredSystemPath(String filename) throws IOException {
		
		Path source = Paths.get(TestFiles.getTestFilePath(filename));
		Path destination = configuredDirectory.toPath().resolve(filename);
		Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
//		Flux.just(1,2,3)
//			.delayElements(Duration.ofSeconds(1))
//			.subscribe(l->{
//				try {
//					Path destination = configuredDirectory.toPath().resolve(filename + l);
//					Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
//				} catch (IOException e) {
//					// TODO Auto-generated catch block
//					e.printStackTrace();
//				}
//			});
		
	}
	
	
	void blockTillScannerReadOrFail(String filename) {
		
		StepVerifier.create(scannerAdapter.flux())
        .expectSubscription() // Ensures the flow starts via your doOnSubscribe
        .then(() -> {
            // Optional: Trigger something here if needed
            log.info("Subscription established, waiting for files...");
        })
        .recordWith(ArrayList::new) // Collect items as they come
        .thenConsumeWhile(fh -> !fh.currentFile().url().contains(filename))
        .assertNext(fh -> {
            log.info("Found file: " + fh.currentFile().url());
            //assertTrue(fh.currentFile().url().contains(filename));
        })
        .thenCancel() // Stop the poller so the test finishes
        .verify(Duration.ofSeconds(15));
		
//		scannerAdapter.flux()
//			.doOnNext(fh-> log.info("" + fh.currentFile().url()))
//			.filter(fh->fh.currentFile().url().contains(filename))
//			.timeout(Duration.ofSeconds(10))
//			.blockFirst();
	}
	
	public void copyTestFileAnAllowToPropagte(String filename) throws IOException {
		
		copyTestFileToConfiguredMonitoredSystemPath(filename);
		blockTillScannerReadOrFail(filename);
		
	}

}

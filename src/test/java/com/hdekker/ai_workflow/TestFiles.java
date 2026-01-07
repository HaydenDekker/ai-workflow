package com.hdekker.ai_workflow;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

import com.hdekker.ai_workflow.files.FileSystemRecursiveFileScannerAdapter;
import com.hdekker.ai_workflow.files.FileSystemScannerConfig;

@Configuration
public class TestFiles {
	
	Logger log = LoggerFactory.getLogger(TestFiles.class);
	
	public static final String TEST_FILES_DIR = "src/test/resources/test-files-init/";
	public static final String FILE_POOR_SOLID_COMPLIANCE = "SOLIDPromptCaller.java";
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
		
		Path destination = configuredDirectory.toPath().resolve(filename);
		Path source = Paths.get(TestFiles.getTestFilePath(filename));
		Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
	}
	
	
	void blockTillScannerReadOrFail(String filename) {
		
		scannerAdapter.flux()
			.doOnNext(fh-> log.info("" + fh.currentFile().url()))
			.filter(fh->fh.currentFile().url().contains(filename))
			.timeout(Duration.ofSeconds(2))
			.blockFirst();
	}
	
	public void copyTestFileAnAllowToPropagte(String filename) throws IOException {
		
		copyTestFileToConfiguredMonitoredSystemPath(filename);
		blockTillScannerReadOrFail(filename);
		
	}

}

package com.hdekker.ai_workflow;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

import com.hdekker.ai_workflow.files.FileSystemRecursiveFileScannerAdapter;
import com.hdekker.ai_workflow.files.FileSystemScannerConfig;

@Configuration
public class TestFiles {
	
	public static final String TEST_FILES_DIR = "src/test/resources/test-files-init/";

	public static final String FILE_POOR_SOLID_COMPLIANCE = "SOLIDPromptCaller.java";
	
	public static String getTestFilePath(String filename) {
		return TEST_FILES_DIR + filename;
	}
	
	@Autowired
	FileSystemScannerConfig fileSystemScannerConfig;
	
	@Autowired
	FileSystemRecursiveFileScannerAdapter scannerAdapter;
	
	File configuredDirectory;
	
	TestFiles(){
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
			.filter(fh->fh.currentFile().url().contains(filename))
			.timeout(Duration.ofSeconds(2))
			.blockFirst();
	}
	
	public void copyTestFileAnAllowToPropagte() throws IOException {
		
		copyTestFileToConfiguredMonitoredSystemPath(TestFiles.FILE_POOR_SOLID_COMPLIANCE);
		blockTillScannerReadOrFail(TestFiles.FILE_POOR_SOLID_COMPLIANCE);
		
	}
}

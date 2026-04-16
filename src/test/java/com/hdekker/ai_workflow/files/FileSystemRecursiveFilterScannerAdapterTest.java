package com.hdekker.ai_workflow.files;

import java.io.IOException;
import java.nio.file.Path;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.hdekker.ai_workflow.TestFiles;
import com.hdekker.ai_workflow.TestProfiles;

@SpringBootTest
@ActiveProfiles(
		{TestProfiles.RESOURCES_TEST_FOLDER,
		TestProfiles.FIXED_LLM_TEST_RESPONSE})
public class FileSystemRecursiveFilterScannerAdapterTest {
	
	@Autowired
	TestFiles testFiles;
	
	@Autowired
	FileSystemRecursiveFileScannerAdapter scannerAdapter;
	
	@TempDir 
	static Path promptDirectory;
	
	@TempDir 
	static Path rootDirectory;
	
	@DynamicPropertySource 
    static void registerTempDirProperty(DynamicPropertyRegistry registry) {
        registry.add("prompt-config.predefinedPromptFilePath", () -> promptDirectory.toAbsolutePath().toString());
        registry.add("scanner.url", () -> "file:/" + rootDirectory.toAbsolutePath().toString());
    }
	
	@Test
	@Disabled
	public void canCaptureFileCreationEvent() throws IOException, InterruptedException {
		testFiles.copyTestFileAnAllowToPropagte(TestFiles.FILE_POOR_SOLID_COMPLIANCE);
		testFiles.copyTestFileAnAllowToPropagte(TestFiles.TYPICAL_RESPONSE_MD);
	}

}

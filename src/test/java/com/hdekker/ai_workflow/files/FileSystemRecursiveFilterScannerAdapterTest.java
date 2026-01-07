package com.hdekker.ai_workflow.files;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

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
	
	@Test
	public void canCaptureFileCreationEvent() throws IOException {
		testFiles.copyTestFileAnAllowToPropagte(TestFiles.FILE_POOR_SOLID_COMPLIANCE);
		testFiles.copyTestFileAnAllowToPropagte(TestFiles.TYPICAL_RESPONSE_MD);
		
	}

}

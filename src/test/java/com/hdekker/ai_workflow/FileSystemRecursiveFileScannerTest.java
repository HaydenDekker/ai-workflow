package com.hdekker.ai_workflow;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class FileSystemRecursiveFileScannerTest {
	
	@Autowired
	FileSystemRecursiveFileScanner fileSystemRecursiveFileScanner;
	
	@Test
	public void onStartup_ScansConfiguredRootFolder() {
		
		assertThat(fileSystemRecursiveFileScanner.files)
			.hasSizeGreaterThan(0);
	
	}
	

}

package com.hdekker.ai_workflow;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.hdekker.ai_workflow.files.FileSystemRecursiveFileScannerAdapter;

@SpringBootTest
public class FileSystemRecursiveFileScannerTest {
	
	@Autowired
	FileSystemRecursiveFileScannerAdapter fileSystemRecursiveFileScanner;
	
	@Test
	public void onStartup_ScansConfiguredRootFolder() throws InterruptedException {
		
		 
		Thread.sleep(50000);
		assertThat(fileSystemRecursiveFileScanner.files)
			.hasSizeGreaterThan(0);
	
	}
	

}

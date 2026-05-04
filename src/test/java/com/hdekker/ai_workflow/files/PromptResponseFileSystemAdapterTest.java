package com.hdekker.ai_workflow.files;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;


import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.hdekker.ai_workflow.TestData;
import com.hdekker.ai_workflow.domain.prompt.PromptResponse;

public class PromptResponseFileSystemAdapterTest {
	
	@TempDir
	Path tempDir;
	
	// TODO check non-existant file is created.
	// TODO existing file is overwritten.
	@Test
	public void givenPromptResponse_ExpectOutputFilePrepared() throws IOException {
		
		PromptResponse testPromptResponse = TestData.basicResponse();
		
		Path file = PromptResponseFileSystemAdapter.createFile(testPromptResponse, tempDir);
		
		Path relativePath = tempDir.relativize(file);
		
		assertThat(relativePath.toString().replace('\\', '/'))
			.isEqualTo("output/input-file.txt");
	
	}

}

package com.hdekker.ai_workflow.files;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.hdekker.ai_workflow.TestData;
import com.hdekker.ai_workflow.prompt.PromptResponse;

public class PromptResponseFileSystemAdapterTest {
	
	@TempDir
	Path tempDir;
	
	@Test
	public void givenPromptResponse_ExpectOutputFilePrepared() {
		
		PromptResponse testPromptResponse = TestData.basicResponse();
		Path file = PromptResponseFileSystemAdapter.createFile(testPromptResponse, tempDir);
		assertThat(file.toFile().getName())
			.isEqualTo(testPromptResponse.prompt().title() + "_" + "input-file.txt" + ".md");
		
	}

}

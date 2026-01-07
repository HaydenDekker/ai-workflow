package com.hdekker.ai_workflow.files;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.hdekker.ai_workflow.prompt.PromptResponse;

public class PromptResponseFileSystemAdapter {
	
	public static Path createFile(PromptResponse testPromptResponse, Path directory) {
		
		Path file = directory.resolve(testPromptResponse.createOutputFileName());
		try {
			BufferedWriter writer = Files.newBufferedWriter(file);
			writer.write(testPromptResponse.response());
			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		return file;
	}

}

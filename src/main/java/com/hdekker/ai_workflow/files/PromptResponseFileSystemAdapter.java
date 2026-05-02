package com.hdekker.ai_workflow.files;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hdekker.ai_workflow.domain.prompt.PromptResponse;

public class PromptResponseFileSystemAdapter {

    private static final Logger log = LoggerFactory.getLogger(PromptResponseFileSystemAdapter.class);
	
	public static Path createFile(PromptResponse testPromptResponse, Path directory) {

		Path file = directory.resolve(testPromptResponse.createOutputFileName());
		log.info("Storing LLM output for file: {} -> {}", testPromptResponse.fileName(), file);
		try {

			if (file.getParent() != null) {
		        Files.createDirectories(file.getParent());
		    }

			BufferedWriter writer = Files.newBufferedWriter(file);
			writer.write(testPromptResponse.response());
			writer.close();

		} catch (IOException e) {
			e.printStackTrace();
		}

		return file;
	}

}

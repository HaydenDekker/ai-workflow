package com.hdekker.ai_workflow.pipeline.management;

import java.io.IOException;
import java.nio.file.Path;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.hdekker.ai_workflow.files.FileSystemFileWriter;
import com.hdekker.ai_workflow.files.FileSystemRecursiveFileScannerAdapter;
import com.hdekker.ai_workflow.files.FileSystemScannerConfig;

@Configuration
public class DynamicPipelineManagerConfiguration {

	@Bean
	public DynamicPipelineManager dynamicPipelineManager(
			FileSystemRecursiveFileScannerAdapter fileScanner,
			FileSystemScannerConfig fileScannerConfig,
			ChatClient chatClient,
			FileSystemFileWriter fileWriter) throws IOException {
		Path outputFolderPath = fileScannerConfig.getUrl().getFile().toPath();
		return new DynamicPipelineManager(fileScanner, fileWriter, outputFolderPath, chatClient);
	}


}

package com.hdekker.ai_workflow.pipeline.management;

import java.io.IOException;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.hdekker.ai_workflow.files.FileSystemRecursiveFileScannerAdapter;
import com.hdekker.ai_workflow.files.FileSystemScannerConfig;

@Configuration
public class DynamicPipelineManagerConfiguration {
	
	@Autowired
	FileSystemRecursiveFileScannerAdapter fileScanner;
	
	@Autowired
	FileSystemScannerConfig fileScannerConfig;
	
	@Autowired
	ChatClient chatClient;
	
	@Bean
	public DynamicPipelineManager dynamicPipelineManager(
			FileSystemRecursiveFileScannerAdapter fileScanner,
			FileSystemScannerConfig fileScannerConfig,
			ChatClient chatClient) throws IOException {
		return new DynamicPipelineManager(fileScanner, fileScannerConfig, chatClient);
	}


}

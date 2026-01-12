package com.hdekker.ai_workflow.pipeline;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hdekker.ai_workflow.app.pipeline.PromptPipelineBuilder;
import com.hdekker.ai_workflow.app.pipeline.PromptPipelineConfigurator;
import com.hdekker.ai_workflow.files.FileSystemRecursiveFileScannerAdapter;
import com.hdekker.ai_workflow.files.FileSystemScannerConfig;
import com.hdekker.ai_workflow.files.PromptResponseFileSystemAdapter;
import com.hdekker.ai_workflow.llm.Prompter;
import com.hdekker.ai_workflow.llm.output.LLMOutputParsingUtils;
import com.hdekker.ai_workflow.pipeline.domain.PipelinePrompt;
import com.hdekker.ai_workflow.pipeline.domain.PromptTriggerEvent;
import com.hdekker.ai_workflow.prompt.PromptConfiguration;
import com.hdekker.ai_workflow.prompt.PromptRequest;
import com.hdekker.ai_workflow.prompt.PromptResponse;
import com.hdekker.ai_workflow.prompt.SystemPromptConfiguration;

import reactor.core.publisher.Flux;

/**
 *  To build the configured pipelines ready for file processing.
 * 
 */
@Configuration
public class PromptPipelineConfiguration {
	
	Logger log = LoggerFactory.getLogger(PromptPipelineConfiguration.class);
	
	@Autowired
	FileSystemScannerConfig fileScannerConfig;

	@Autowired
	FileSystemRecursiveFileScannerAdapter fileScanner;
	
	@Autowired
	PromptConfiguration promptConfiguration;
	
	ObjectMapper om = new ObjectMapper();
	
	SplittableStrategy jsonItemListConverter = (s)->{
		String json = LLMOutputParsingUtils.extractJsonContent(s.response());
		List<Object> list = List.of();
		try {
			list = om.readValue(json, new TypeReference<List<Object>>() {});
		} catch (JsonProcessingException e) {
			log.error("Unexpected LLM response " + s.response());
			e.printStackTrace();
		}
		return list
			.stream()
			.map(resp->new PromptResponse(s.prompt(), s.fileName(), s.fileContents(), resp.toString()))
			.toList();
	};
	
	@Autowired
	SystemPromptConfiguration systemPromptConfiguration;
	
	@Autowired
	Prompter prompter;
	
	// TODO component and pass in.
	Path outputFolderPath;
	
	public PromptPipelineConfiguration(
			FileSystemRecursiveFileScannerAdapter fileScanner,
			PromptConfiguration promptConfiguration,
			SystemPromptConfiguration systemPromptConfiguration,
			FileSystemScannerConfig fileScannerConfig,
			Prompter prompter) {
		
		this.fileScanner = fileScanner;
		this.promptConfiguration = promptConfiguration;
		this.systemPromptConfiguration = systemPromptConfiguration;
		this.fileScannerConfig = fileScannerConfig;
		this.prompter = prompter;

		try {
			outputFolderPath = fileScannerConfig.getUrl().getFile().toPath();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		Consumer<PromptResponse> persisterAdapter = pr-> PromptResponseFileSystemAdapter.createFile(pr, outputFolderPath);
		
		// TODO swap in
		PromptPipelineConfigurator ppc = new PromptPipelineConfigurator(
				fileScanner.flux(),
				prompter,
				persisterAdapter
				);
	
		systemPromptConfiguration.getPromptChains()
			.stream()
			.peek(pc-> log.info("Configuring " + pc.chain().get(0).title()))
			.flatMap(pc-> ppc.configure(pc.chain()).stream())
			.forEach(flux-> {
				log.info("starting flux");
				flux.subscribe();
			});
		
	}
	
	

}

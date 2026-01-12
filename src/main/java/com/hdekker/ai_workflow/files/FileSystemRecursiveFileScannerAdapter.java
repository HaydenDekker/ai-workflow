package com.hdekker.ai_workflow.files;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.integration.channel.FluxMessageChannel;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.IntegrationFlowBuilder;
import org.springframework.integration.dsl.Pollers;
import org.springframework.integration.dsl.StandardIntegrationFlow;
import org.springframework.integration.dsl.context.IntegrationFlowContext;
import org.springframework.integration.file.FileReadingMessageSource.WatchEventType;
import org.springframework.integration.file.dsl.Files;
import org.springframework.integration.util.IntegrationReactiveUtils;
import org.springframework.stereotype.Component;

import com.hdekker.ai_workflow.database.filemetadata.FileMetadataDatabase;
import com.hdekker.ai_workflow.files.domain.FileMetadata;
import com.hdekker.ai_workflow.llm.OllamaWorld;

import reactor.core.publisher.Flux;

@Component
public class FileSystemRecursiveFileScannerAdapter{
	
	Logger log = LoggerFactory.getLogger(FileSystemRecursiveFileScannerAdapter.class);
	
	public List<URI> files = new ArrayList<URI>();
	
	@Autowired
	FileSystemScannerConfig config;
	
	@Autowired
	IntegrationFlowContext context;
	
	BufferedWriter bw = null;
	
	Flux<FileHistory> flux = Flux.empty();
	
	@Autowired
	ApplicationContext applicationContext;
	
	StandardIntegrationFlow flow;
	
	@Autowired
	FileMetadataDatabase fileMetadataDatabase;
	
	FileSystemRecursiveFileScannerAdapter(FileSystemScannerConfig config,
			IntegrationFlowContext context,
			OllamaWorld ollamaWorld,
			ApplicationContext applicationContext,
			FileMetadataDatabase fileMetadataDatabase){
		
		this.config = config;
		this.applicationContext = applicationContext;
		this.fileMetadataDatabase = fileMetadataDatabase;
		
		File folder = null;
		try {
			folder = config.url.getFile().getAbsoluteFile();
			log.info("Absolute path for project root: " + folder.getPath());
		} catch (IOException e) {
			e.printStackTrace();
			return;
		}

		IntegrationFlowBuilder flowBuilder = IntegrationFlow.from(
				Files.inboundAdapter(folder)
					.recursive(true)
					.useWatchService(true)
					.watchEvents(
							WatchEventType.CREATE, 
							WatchEventType.MODIFY, 
							WatchEventType.DELETE),
				e-> e.poller(
						Pollers.fixedRate(Duration.ofMillis(10), Duration.ofSeconds(2)))
			)
			.log()
			.transform(Files.toStringTransformer())
			.channel(c-> c.flux("fileInboundFluxChannel"));
		
		flow = flowBuilder.get();
		
		context.registration(flow)
				.id("reactiveFileInputFlow")
				.register();

		FluxMessageChannel filesChannel = this.applicationContext
	            .getBean("fileInboundFluxChannel", FluxMessageChannel.class);
		
		FileComparator fileComparator = new FileComparator(fileMetadataDatabase);
		
		
		flux = IntegrationReactiveUtils.messageChannelToFlux(filesChannel)
					.map(m->{
						String s = (String) m.getPayload();
						String hash = FileHash.hash(s);
						String file = (String) m.getHeaders().get("file_relativePath");
						return new FileMetadata(file, s, hash);	
					})
					.map(fileComparator::matches)
					.filter(fh->!fh.hashMatches())
					.doOnNext(fh->fileMetadataDatabase.save(fh.currentFile()))
					.share();
		
	}
	
	public Flux<FileHistory> flux() {
		return flux.onBackpressureBuffer();
	}


}

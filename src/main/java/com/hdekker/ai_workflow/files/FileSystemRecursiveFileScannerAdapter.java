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
import org.springframework.integration.file.dsl.Files;
import org.springframework.integration.util.IntegrationReactiveUtils;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

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
	
	Flux<FileMetadata> flux = Flux.empty();
	
	@Autowired
	ApplicationContext applicationContext;
	
	StandardIntegrationFlow flow;
	
	FileSystemRecursiveFileScannerAdapter(FileSystemScannerConfig config,
			IntegrationFlowContext context,
			OllamaWorld ollamaWorld,
			ApplicationContext applicationContext){
		
		this.config = config;
		this.applicationContext = applicationContext;
		
		//DateTimeFormatter form = DateTimeFormatter.ofPattern("ddMMHHSS");
		//File logFile = new File(config.getOutputFolder() + "/log_" + LocalDateTime.now().format(form) + ".json");
		File folder = null;
		try {
			//bw = new BufferedWriter(new FileWriter(logFile));
			folder = config.url.getFile();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return;
		}

		IntegrationFlowBuilder flowBuilder = IntegrationFlow.from(
				Files.inboundAdapter(folder)
					.recursive(true)
					.patternFilter("*.java"),
				e-> e.poller(
						Pollers.fixedRate(Duration.ofSeconds(1), Duration.ofSeconds(2)))
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
		
		FileHash fileHash = new FileHash();
		
		flux = IntegrationReactiveUtils.messageChannelToFlux(filesChannel)
					.map(m->{
						String s = (String) m.getPayload();
						String hash = fileHash.hash(s);
						String file = (String) m.getHeaders().get("file_relativePath");
						return new FileMetadata(file, s, hash);	
					});
		
		
	}
	
	public Flux<FileMetadata> flux() {
		return flux;
	}


}

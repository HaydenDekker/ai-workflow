package com.hdekker.ai_workflow.llm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Flux;


@Configuration
public class OllamaWorld {
	
	Logger log = LoggerFactory.getLogger(OllamaWorld.class);
	
	OllamaChatModel ollamaChatModel;
	
	ChatClient client;
	
	public OllamaWorld(OllamaChatModel ollamaChatModel) {
		
		this.ollamaChatModel = ollamaChatModel;
		
		log.info("calling model");
		
		client = ChatClient.builder(ollamaChatModel).build();
		
	}
	
	
	public Flux<String> call(String prompt) {
		return client.prompt(prompt)
				.stream()
				.content();
			
	}

}

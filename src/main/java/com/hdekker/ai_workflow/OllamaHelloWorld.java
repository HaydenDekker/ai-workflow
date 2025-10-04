package com.hdekker.ai_workflow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.context.annotation.Configuration;


@Configuration
public class OllamaHelloWorld {
	
	Logger log = LoggerFactory.getLogger(OllamaHelloWorld.class);
	
	OllamaChatModel ollamaChatModel;
	
	public OllamaHelloWorld(OllamaChatModel ollamaChatModel) {
		
		this.ollamaChatModel = ollamaChatModel;
		
		log.info("calling model");
		
		ChatClient client = ChatClient.builder(ollamaChatModel).build();
		
//		String content = client.prompt("Hello, 1+1 is?")
//		.call()
//		.content();
		
		//log.info(content);
		
	}

}

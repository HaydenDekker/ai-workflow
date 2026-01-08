package com.hdekker.ai_workflow.llm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import reactor.core.publisher.Flux;


@Service
public class OllamaWorld implements Prompter{
	
	Logger log = LoggerFactory.getLogger(OllamaWorld.class);
	
	@Autowired
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

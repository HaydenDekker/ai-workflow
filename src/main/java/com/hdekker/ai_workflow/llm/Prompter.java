package com.hdekker.ai_workflow.llm;

import reactor.core.publisher.Flux;

public interface Prompter {
	Flux<String> call(String prompt, String outputStructure);
}

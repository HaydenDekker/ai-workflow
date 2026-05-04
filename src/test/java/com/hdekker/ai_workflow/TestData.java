package com.hdekker.ai_workflow;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;


import org.junit.jupiter.api.Test;

import com.hdekker.ai_workflow.domain.agent.AgentDefinition;
import com.hdekker.ai_workflow.domain.prompt.PromptRequest;
import com.hdekker.ai_workflow.domain.prompt.PromptResponse;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.TextReader;
import org.springframework.core.io.ByteArrayResource;

public class TestData {
	
	public static String fileNameStub = "input-file.txt";
	static String fileContentStub = "This is the content of the input file.";
	
	public static AgentDefinition basicPrompt() {
		return new AgentDefinition( 
				"(?:.*/)?(?<name>.*\\.txt)",
				"BASIC PROMPT TEST", 
				"STANDARD",
				"This prompt is part of a basic pipeline stage configuration. You should simply confirm you've received this prompt.", 
				"Neat and tidy output is required.",
				"output/${name}",
				System.getProperty("java.io.tmpdir") + "/ai-workflow-test");
	}
	
	public static PromptResponse basicResponse() {
		
		PromptRequest br = basicRequest(fileNameStub);
		
		return new PromptResponse(
				basicPrompt(), 
				br.fileURL(),
				br.file(), 
				"This is the content after the pipeline stage has parsed the input file via an llm and appended any additional information.");
		
	}

	public static PromptRequest basicRequest(String fileName) {
		return new PromptRequest(fileContentStub, fileName);
	}
	
	// TODO include the spring document abstraction in prompt req, resp.
	public static Document createDocument(String body, String fileName) {
		
		ByteArrayResource resource = new ByteArrayResource(body.getBytes()) {
		    @Override
		    public String getFilename() {
		        return fileName;
		    }
		};
		
		TextReader textReader = new TextReader(resource);
		List<Document> document = textReader.read();
		return document.get(0);
		
	}
	
	@Test
	public void canCreateTextDocument() {
		
		PromptRequest br = basicRequest(fileNameStub);
		Document document = createDocument(br.file(), br.fileURL());
		
		assertThat(document)
			.isNotNull();
		
		
	}

}

package com.hdekker.ai_workflow.database.promptresponse;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;

/**
 * 
 */
/**
 * 
 */
@Entity
public class PromptResponseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	public Integer id;
	
	String promptTitle;
	
	@Lob
	public String promptInput;
	
	@Lob
	public String response;
	
	
	String fileName;

	
	public String getPromptInput() {
		return promptInput;
	}

	public void setPromptInput(String promptInput) {
		this.promptInput = promptInput;
	}

	public String getFileName() {
		return fileName;
	}

	public void setFileName(String fileName) {
		this.fileName = fileName;
	}

	public String getPromptTitle() {
		return promptTitle;
	}

	public void setPromptTitle(String promptTitle) {
		this.promptTitle = promptTitle;
	}

	public Integer getId() {
		return id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	public String getResponse() {
		return response;
	}

	public void setResponse(String response) {
		this.response = response;
	}
	
}

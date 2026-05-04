package com.hdekker.ai_workflow.adapter.outbound.persistence.filemetadata;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class FileMetadataEntity{
	
	@Id
	String url;
	String hash;
	
	public String getUrl() {
		return url;
	}
	public void setUrl(String url) {
		this.url = url;
	}
	public String getHash() {
		return hash;
	}
	public void setHash(String hash) {
		this.hash = hash;
	}
	
}

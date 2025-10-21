package com.hdekker.ai_workflow.database.solid;

import com.hdekker.ai_workflow.llm.output.SOLIDCompliance.COMPLIANT;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;

/**
 * 
 */
@Entity
public class SolidComplianceEntity {
	
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	Integer id;
	
	String fileHash;
	String className;
	COMPLIANT compliance;
	String principle;
	@Lob
	String task;
	@Lob
	String reason;
	

	public Integer getId() {
		return id;
	}
	public void setId(Integer id) {
		this.id = id;
	}
	public String getFileHash() {
		return fileHash;
	}
	public void setFileHash(String fileHash) {
		this.fileHash = fileHash;
	}
	public String getClassName() {
		return className;
	}
	public void setClassName(String className) {
		this.className = className;
	}
	public COMPLIANT getCompliance() {
		return compliance;
	}
	public void setCompliance(COMPLIANT compliance) {
		this.compliance = compliance;
	}
	public String getPrinciple() {
		return principle;
	}
	public void setPrinciple(String principle) {
		this.principle = principle;
	}
	public String getTask() {
		return task;
	}
	public void setTask(String task) {
		this.task = task;
	}
	public String getReason() {
		return reason;
	}
	public void setReason(String reason) {
		this.reason = reason;
	}
	
	

}

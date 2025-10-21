package com.hdekker.ai_workflow.llm.output;

public record SOLIDCompliance(
		String className, 
		COMPLIANT compliance, 
		String principle,
		String task, 
		String reason
		) {
	
	public enum COMPLIANT {
		YES,
		NO
	}

}

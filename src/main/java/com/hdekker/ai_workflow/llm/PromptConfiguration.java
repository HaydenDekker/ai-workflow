package com.hdekker.ai_workflow.llm;

public class PromptConfiguration {
	
	public static final String PRIORITY_ORDER_PROMPT = """
			Given this list of review responses, the question and the source 
			file, prioritise the items in order of most impactful change to 
			least impactful change.
			""";

}

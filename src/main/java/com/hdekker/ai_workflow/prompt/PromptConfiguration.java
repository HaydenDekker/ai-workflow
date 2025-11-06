package com.hdekker.ai_workflow.prompt;

public class PromptConfiguration {
	
	public static final String SOLID_COMPLIANCE_PROMPT_TITLE = "SOLID_COMPLIANCE";
	
	public static final String SOLID_COMPLAINCE_PROMPT = """
			Given the code below, ensure that it complies to SOLID principals. 
			You might not have external context for some classes so the assessment should just reason about the immediate code. 
			If it doesn't immediately break principals state that it is compliant.
			\n\r ---------- \n\r 
			""";
	
	public static final String SOLID_COMPLIANCE_PROMPT_OUTPUT = "Output json, as {className:String, compliance: YES|NO, principle:String, task:String, reason:String}";
	
	
	public static final String PRIORITY_ORDER_PROMPT_TITLE = "PRIORITY_ORDER";
	
	public static final String PRIORITY_ORDER_PROMPT = """
			Given this list of review responses, the question and the source 
			file, prioritise the items in order of most impactful change to 
			least impactful change. Use the number 10 to 1 with 10 being most impactful.
			""";
	
	public static final String PRIORITY_ORDER_PROMPT_OUTPUT = """
			Provide response in the following json format,
			{
				inputSummary: String,
				impactAssessment: String,
				imaactWeight: Number 1 to 10
			}
			If there are no responses, respond with an empty list.
			If there is only one respone, make sure its wrapped in a list.
			""";


}

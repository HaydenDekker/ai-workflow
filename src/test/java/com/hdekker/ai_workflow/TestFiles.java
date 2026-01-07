package com.hdekker.ai_workflow;

public class TestFiles {
	
	public static final String TEST_FILES_DIR = "src/test/resources/test-files-init/";

	public static final String FILE_POOR_SOLID_COMPLIANCE = "SOLIDPromptCaller.java";
	
	public static String getTestFilePath(String filename) {
		return TEST_FILES_DIR + filename;
	}
}

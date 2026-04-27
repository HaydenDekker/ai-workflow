package com.hdekker.ai_workflow;

/**
 * Test constants shared across test classes.
 */
public final class TestConstants {

	private TestConstants() {}

	public static final String TEST_FILES_DIR = "src/test/resources/test-files-init/";
	public static final String FILE_POOR_SOLID_COMPLIANCE = "SOLIDPromptCaller.java";
	public static final String FILE_SOLID_NON_COMPLIANCE_OUTPUT_DIR = "SOLID_NON_COMPLIANCE";
	public static final String TYPICAL_RESPONSE_MD = "typical_response.md";

	public static String getTestFilePath(String filename) {
		return TEST_FILES_DIR + filename;
	}
}

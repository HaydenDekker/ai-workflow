package com.hdekker.ai_workflow.prompt;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.hdekker.ai_workflow.TestProfiles;

/**
 *  Need to allow prompts packaged with this source
 *  out of the box. YAML isn't ideal. File system hopeful.
 *  Prompt config packaged with prompts makes sense.
 * 
 */
@SpringBootTest
@ActiveProfiles(TestProfiles.RESOURCES_TEST_FOLDER)
public class PromptConfigurationTest {
	
	@Autowired
	SystemPromptConfiguration systemPromptConfiguration;
	
	@TempDir 
	static Path directory;
	
	// TODO - Replace all directory access for testing with temp directory instances
	// including prompt directory and root directory
	@DynamicPropertySource 
    static void registerTempDirProperty(DynamicPropertyRegistry registry) {
        registry.add("prompt-config.predefinedPromptFilePath", () -> directory.toAbsolutePath().toString());
    }
	
	@Test
	public void onStart_expectInternalPromptFolderCopiedToLocalSystemPromptFolder() throws IOException {
		
		assertThat(systemPromptConfiguration.getCopiedLocally())
			.isTrue();
		 
		assertThat(Files.list(directory).count())
			.isGreaterThan(1);
		
	}
	
	@Test
	public void onStart_expectTestPromptConfigurationsReadIntoMemory() {
		
		assertThat(systemPromptConfiguration.getPromptChains())
			.hasSizeGreaterThan(1);
		
	}

}

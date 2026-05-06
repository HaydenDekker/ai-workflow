package com.hdekker.ai_workflow.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;


import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Tests {@link SystemPromptConfiguration} in isolation — verifies that
 * YAML workflow definitions are copied from the classpath and parsed
 * into memory without triggering the full application context
 * (which would persist YAML agents to the database).
 *
 * <p>Uses {@code @SpringBootTest(classes = TestConfig.class)} as recommended
 * by the Spring Boot docs to load only the beans needed for the test.</p>
 */
@SpringBootTest(classes = PromptConfigurationTest.TestConfig.class)
class PromptConfigurationTest {

	@TempDir
	static Path tempDir;

	@DynamicPropertySource
	static void registerProperties(DynamicPropertyRegistry registry) {
		registry.add("prompt-config.predefinedPromptFilePath", () -> tempDir.toAbsolutePath().toString());
		registry.add("yaml.agents.enabled", () -> "false");
	}

	@Autowired
	SystemPromptConfiguration systemPromptConfiguration;

	@Test
	void internalPromptFolderCopiedToLocalSystemPromptFolder() throws IOException {
		assertThat(systemPromptConfiguration.getCopiedLocally())
				.isTrue();

		try (Stream<Path> entries = Files.list(tempDir)) {
			assertThat(entries.count())
					.isGreaterThan(1);
		}
	}

	@Test
	void promptConfigurationsReadIntoMemory() {
		assertThat(systemPromptConfiguration.getAgentWorkflows())
				.hasSizeGreaterThan(1);
	}

	/**
	 * Minimal test configuration that provides only the beans needed
	 * to exercise {@link SystemPromptConfiguration} in isolation.
	 * Per the Spring Boot testing docs, this avoids loading the full
	 * application context (which would persist YAML agents to the DB).
	 */
	@Configuration
	@EnableConfigurationProperties(PromptConfiguration.class)
	static class TestConfig {

		@Bean
		SystemPromptConfiguration systemPromptConfiguration(PromptConfiguration promptConfiguration,
				ResourcePatternResolver resourcePatternResolver) {
			return new SystemPromptConfiguration(promptConfiguration, resourcePatternResolver);
		}
	}
}

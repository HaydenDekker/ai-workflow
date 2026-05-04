package com.hdekker.ai_workflow.files;

import java.nio.file.Path;
import java.util.function.Consumer;

import com.hdekker.ai_workflow.domain.prompt.PromptResponse;

public interface FileWriter {
    Consumer<PromptResponse> createPersister(Path outputDirectory);
}
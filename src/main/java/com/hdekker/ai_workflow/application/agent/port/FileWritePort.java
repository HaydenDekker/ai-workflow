package com.hdekker.ai_workflow.application.agent.port;

import java.nio.file.Path;
import java.util.function.Consumer;

import com.hdekker.ai_workflow.domain.prompt.PromptResponse;

/**
 * Port interface for writing LLM prompt responses to persistent storage.
 * <p>
 * The application layer uses this to persist generated output without
 * knowing the underlying filesystem or persistence mechanism.
 */
public interface FileWritePort {

    /**
     * Creates a persister consumer for the given output directory.
     * The returned consumer writes a single {@link PromptResponse} to disk.
     *
     * @param outputDirectory the directory to write responses into
     * @return a consumer that persists a prompt response
     */
    Consumer<PromptResponse> createPersister(Path outputDirectory);
}

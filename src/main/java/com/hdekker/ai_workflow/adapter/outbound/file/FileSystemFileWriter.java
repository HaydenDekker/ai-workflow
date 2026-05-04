package com.hdekker.ai_workflow.adapter.outbound.file;

import java.nio.file.Path;
import java.util.function.Consumer;

import com.hdekker.ai_workflow.application.agent.port.FileWritePort;
import com.hdekker.ai_workflow.domain.prompt.PromptResponse;

import org.springframework.stereotype.Component;

@Component
public class FileSystemFileWriter implements FileWriter, FileWritePort {

    @Override
    public Consumer<PromptResponse> createPersister(Path outputDirectory) {
        return pr -> PromptResponseFileSystemAdapter.createFile(pr, outputDirectory);
    }
}
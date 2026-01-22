package com.hdekker.ai_workflow.files;

import java.nio.file.Path;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;
import com.hdekker.ai_workflow.prompt.PromptResponse;

@Component
public class FileSystemFileWriter implements FileWriter {

    @Override
    public Consumer<PromptResponse> createPersister(Path outputDirectory) {
        return pr -> PromptResponseFileSystemAdapter.createFile(pr, outputDirectory);
    }
}
package com.hdekker.ai_workflow.files;

import reactor.core.publisher.Flux;
import com.hdekker.ai_workflow.files.FileHistory;

public interface FileScanner {
    Flux<FileHistory> flux();
}
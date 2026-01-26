package com.hdekker.ai_workflow.files;

import reactor.core.publisher.Flux;

public interface FileScanner {
    Flux<FileHistory> flux();
}
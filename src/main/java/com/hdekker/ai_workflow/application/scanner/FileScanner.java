package com.hdekker.ai_workflow.application.scanner;

import com.hdekker.ai_workflow.domain.file.FileHistory;

import reactor.core.publisher.Flux;

/**
 * Interface for a file scanner that emits file history events.
 * <p>
 * Application-layer contract — implementations (ScannerService) provide
 * the flux of file change events for downstream processing.
 */
public interface FileScanner {
	/**
	 * Returns the flux of file history events.
	 * Subscribers receive incremental updates from the watch service.
	 *
	 * @return reactive stream of file history events
	 */
	Flux<FileHistory> flux();
}

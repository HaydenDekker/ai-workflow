package com.hdekker.ai_workflow.files;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hdekker.ai_workflow.files.FileMetadataStore;
import com.hdekker.ai_workflow.files.domain.FileMetadata;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

/**
 * Parameterised file scanner adapter for use with {@link com.hdekker.ai_workflow.app.pipeline.management.ScannerRegistry}.
 * <p>
 * This adapter accepts its folder path and delay at construction time, enabling one adapter per agent.
 * <p>
 * Lifecycle is managed externally: the adapter uses a native NIO WatchService pipeline
 * for watching the target directory.
 */
public class FileSystemScannerAdapter implements FileScanner {

    private static final Logger log = LoggerFactory.getLogger(FileSystemScannerAdapter.class);

    private final String folderPath;
    private final Duration delayBetweenReads;
    private final FileMetadataStore fileMetadataStore;

    private final NativeFileWatcher nativeFileWatcher;
    private volatile boolean disposed = false;

    /**
     * Creates a new parameterised scanner adapter.
     *
     * @param folderPath          absolute path to watch
     * @param delayBetweenReads   poll interval for the watch service
     * @param fileMetadataStore metadata for change detection
     */
    public FileSystemScannerAdapter(String folderPath,
                                     Duration delayBetweenReads,
                                     FileMetadataStore fileMetadataStore) {
        this.folderPath = folderPath;
        this.delayBetweenReads = delayBetweenReads;
        this.fileMetadataStore = fileMetadataStore;
        this.nativeFileWatcher = new NativeFileWatcher(Path.of(folderPath), delayBetweenReads, fileMetadataStore);
        initSource();
    }

    /**
     * Initialise the native file watcher for watching the target directory.
     */
    private void initSource() {
        try {
            Path folder = Path.of(folderPath).toAbsolutePath();
            log.info("Setting up scanner for folder: {}", folder);

            nativeFileWatcher.start();

            log.info("Scanner initialised for folder: {}", folderPath);

        } catch (Exception e) {
            log.error("Failed to initialise scanner for folder: {}", folderPath, e);
        }
    }

    /**
     * Returns the flux of file changes. Subscribers receive incremental updates
     * from the watch service.
     */
    @Override
    public Flux<FileHistory> flux() {
        return nativeFileWatcher.flux().onBackpressureBuffer();
    }

    /**
     * Reset the scanner to full-scan mode.
     * Emits all files from the target directory through the existing flux.
     * The watch service continues to emit incremental changes.
     */
    public void resetToFullScan() {
        log.info("Resetting scanner to full scan at: {}", folderPath);
        scanAllFiles();
        log.info("Full scan complete for: {}", folderPath);
    }

    /**
     * Scan all files in the target directory and emit new ones through the sink.
     */
    private void scanAllFiles() {
        try {
            Path folder = Path.of(folderPath).toAbsolutePath();
            if (!Files.exists(folder)) {
                log.warn("Target folder does not exist: {}", folderPath);
                return;
            }

            FileComparator comparator = new FileComparator(fileMetadataStore);

            Files.walk(folder)
                    .filter(Files::isRegularFile)
                    .forEach(p -> {
                        try {
                            String content = Files.readString(p);
                            String hash = FileHash.hash(content);
                            String relativePath = folder.relativize(p).toString().replace("\\", "/");
                            FileMetadata metadata = new FileMetadata(relativePath, content, hash);
                            FileHistory history = comparator.matches(metadata);

                            if (!history.hashMatches()) {
                                log.debug("Full scan - emitting new file: {}", p);
                                fileMetadataStore.save(metadata);
                                nativeFileWatcher.emit(history);
                            } else {
                                log.debug("Full scan - skipping existing file: {}", p);
                            }
                        } catch (IOException e) {
                            log.warn("Failed to read file during full scan: {}", p, e);
                        }
                    });
        } catch (IOException e) {
            log.error("Failed to walk folder during full scan: {}", folderPath, e);
        }
    }

    /**
     * Destroy the scanner and clean up resources.
     */
    public void destroy() {
        if (disposed) {
            return;
        }
        disposed = true;
        nativeFileWatcher.stop();
        log.info("Scanner destroyed for folder: {}", folderPath);
    }

    /**
     * Check if this adapter has been destroyed.
     */
    public boolean isDisposed() {
        return disposed;
    }

    /**
     * Get the folder path this adapter is watching.
     */
    public String getFolderPath() {
        return folderPath;
    }
}

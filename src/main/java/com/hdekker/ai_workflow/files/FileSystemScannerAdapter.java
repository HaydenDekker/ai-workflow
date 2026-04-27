package com.hdekker.ai_workflow.files;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.integration.file.DefaultDirectoryScanner;
import org.springframework.integration.file.inbound.FileReadingMessageSource;
import org.springframework.integration.util.IntegrationReactiveUtils;
import org.springframework.messaging.Message;

import com.hdekker.ai_workflow.database.filemetadata.FileMetadataDatabase;
import com.hdekker.ai_workflow.files.domain.FileMetadata;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

/**
 * Parameterised file scanner adapter for use with {@link com.hdekker.ai_workflow.app.pipeline.management.ScannerRegistry}.
 * <p>
 * Unlike {@link FileSystemRecursiveFileScannerAdapter} which is a Spring singleton tied to a single path,
 * this adapter accepts its folder path and delay at construction time, enabling one adapter per agent.
 * <p>
 * Lifecycle is managed externally: the adapter uses a direct {@code FileReadingMessageSource} pipeline
 * (no {@code IntegrationFlowContext}) for watching the target directory.
 */
public class FileSystemScannerAdapter implements FileScanner {

    private static final Logger log = LoggerFactory.getLogger(FileSystemScannerAdapter.class);

    private final String folderPath;
    private final Duration delayBetweenReads;
    private final FileMetadataDatabase fileMetadataDatabase;

    private final Sinks.Many<FileHistory> sink;
    private volatile FileReadingMessageSource messageSource;
    private volatile boolean disposed = false;

    /**
     * Creates a new parameterised scanner adapter.
     *
     * @param folderPath          absolute path to watch
     * @param delayBetweenReads   poll interval for the watch service
     * @param fileMetadataDatabase metadata for change detection
     */
    public FileSystemScannerAdapter(String folderPath,
                                     Duration delayBetweenReads,
                                     FileMetadataDatabase fileMetadataDatabase) {
        this.folderPath = folderPath;
        this.delayBetweenReads = delayBetweenReads;
        this.fileMetadataDatabase = fileMetadataDatabase;
        this.sink = Sinks.many().multicast().directBestEffort();
        initSource();
    }

    /**
     * Initialise the direct FileReadingMessageSource pipeline for watching the target directory.
     */
    private void initSource() {
        try {
            Path folder = Path.of(folderPath).toAbsolutePath();
            log.info("Setting up scanner for folder: {}", folder);

            messageSource = new FileReadingMessageSource();
            messageSource.setDirectory(folder.toFile());
            // Configure scanner to return all files (needed for watch service to work)
            DefaultDirectoryScanner scanner = new DefaultDirectoryScanner();
            scanner.setFilter(files -> Arrays.asList(files));
            messageSource.setScanner(scanner);
            messageSource.setUseWatchService(true);
            messageSource.setWatchEvents(
                    FileReadingMessageSource.WatchEventType.CREATE,
                    FileReadingMessageSource.WatchEventType.MODIFY,
                    FileReadingMessageSource.WatchEventType.DELETE);
            // Provide a minimal application context with required integration beans
            // to prevent "No such bean 'integrationEvaluationContext'" errors
            // when scanning directories outside of Spring context
            GenericApplicationContext appCtx = new GenericApplicationContext();
            // Register the evaluation context bean before refresh
            appCtx.getBeanFactory().registerSingleton(
                    "integrationEvaluationContext",
                    new StandardEvaluationContext());
            appCtx.refresh();
            messageSource.setBeanFactory(appCtx.getBeanFactory());

            // Build the reactive pipeline directly from source to sink
            Flux<FileHistory> sourceFlux = IntegrationReactiveUtils.messageSourceToFlux(messageSource)
                    .doOnSubscribe(s -> log.info("Starting scanner at {}", folderPath))
                    .map(Message::getPayload)
                    .map(file -> {
                        try {
                            String content = Files.readString(file.toPath());
                            String hash = FileHash.hash(content);
                            String relativePath = folder.relativize(file.toPath()).toString().replace("\\", "/");
                            return Optional.of(new FileMetadata(relativePath, content, hash));
                        } catch (IOException e) {
                            log.warn("Failed to read file: {}", file, e);
                            return Optional.<FileMetadata>empty();
                        }
                    })
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .map(metadata -> fileComparator().matches(metadata))
                    .filter(fh -> {
                        boolean passes = !fh.hashMatches();
                        log.debug("Filter result for {}: {}", fh.currentFile().url(), passes);
                        return passes;
                    })
                    .doOnNext(fh -> {
                        log.debug("Saving to database: {}", fh.currentFile().url());
                        fileMetadataDatabase.save(fh.currentFile());
                    })
                    .onBackpressureBuffer();

            sourceFlux.subscribe(sink::tryEmitNext);

            log.info("Scanner initialised for folder: {}", folderPath);

        } catch (Exception e) {
            log.error("Failed to initialise scanner for folder: {}", folderPath, e);
        }
    }

    /**
     * Create a FileComparator for the given database.
     */
    private FileComparator fileComparator() {
        return new FileComparator(fileMetadataDatabase);
    }

    /**
     * Returns the flux of file changes. Subscribers receive incremental updates
     * from the watch service.
     */
    @Override
    public Flux<FileHistory> flux() {
        return sink.asFlux().onBackpressureBuffer();
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

            FileComparator comparator = fileComparator();

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
                                fileMetadataDatabase.save(metadata);
                                sink.tryEmitNext(history);
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
        sink.tryEmitComplete();
        if (messageSource != null) {
            messageSource.stop();
        }
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

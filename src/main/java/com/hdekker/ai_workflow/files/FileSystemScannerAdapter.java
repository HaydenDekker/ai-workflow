package com.hdekker.ai_workflow.files;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.integration.channel.FluxMessageChannel;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.IntegrationFlowBuilder;
import org.springframework.integration.dsl.Pollers;
import org.springframework.integration.dsl.context.IntegrationFlowContext;
import org.springframework.integration.dsl.context.IntegrationFlowContext.IntegrationFlowRegistration;
import org.springframework.integration.file.FileReadingMessageSource;
import org.springframework.integration.util.IntegrationReactiveUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import com.hdekker.ai_workflow.database.filemetadata.FileMetadataDatabase;
import com.hdekker.ai_workflow.files.domain.FileMetadata;

/**
 * Parameterised file scanner adapter for use with {@link com.hdekker.ai_workflow.app.pipeline.management.ScannerRegistry}.
 * <p>
 * Unlike {@link FileSystemRecursiveFileScannerAdapter} which is a Spring singleton tied to a single path,
 * this adapter accepts its folder path and delay at construction time, enabling one adapter per agent.
 * <p>
 * Lifecycle is managed externally: the adapter creates an {@code IntegrationFlowRegistration} for the
 * watch-service flow, which must be destroyed when the scanner is no longer needed.
 */
public class FileSystemScannerAdapter implements FileScanner {

    private static final Logger log = LoggerFactory.getLogger(FileSystemScannerAdapter.class);

    private final String folderPath;
    private final Duration delayBetweenReads;
    private final IntegrationFlowContext integrationFlowContext;
    private final FileMetadataDatabase fileMetadataDatabase;

    private final Sinks.Many<FileHistory> sink;
    private IntegrationFlowRegistration flowRegistration;
    private volatile boolean disposed = false;

    /**
     * Creates a new parameterised scanner adapter.
     *
     * @param folderPath          absolute path to watch
     * @param delayBetweenReads   poll interval for the watch service
     * @param integrationFlowContext Spring Integration flow context
     * @param fileMetadataDatabase metadata database for change detection
     */
    public FileSystemScannerAdapter(String folderPath,
                                     Duration delayBetweenReads,
                                     IntegrationFlowContext integrationFlowContext,
                                     FileMetadataDatabase fileMetadataDatabase) {
        this.folderPath = folderPath;
        this.delayBetweenReads = delayBetweenReads;
        this.integrationFlowContext = integrationFlowContext;
        this.fileMetadataDatabase = fileMetadataDatabase;
        this.sink = Sinks.many().multicast().directBestEffort();
        this.flowRegistration = null;
        initFlow();
    }

    /**
     * Initialise the Spring Integration flow for watching the target directory.
     */
    private void initFlow() {
        try {
            Path folder = Path.of(folderPath).toAbsolutePath();
            log.info("Setting up scanner flow for folder: {}", folder);

            // Use fully qualified name to avoid conflict with java.nio.file.Files
            IntegrationFlowBuilder flowBuilder = IntegrationFlow.from(
                            org.springframework.integration.file.dsl.Files.inboundAdapter(folder.toFile())
                                    .recursive(true)
                                    .useWatchService(true)
                                    .watchEvents(
                                            FileReadingMessageSource.WatchEventType.CREATE,
                                            FileReadingMessageSource.WatchEventType.MODIFY,
                                            FileReadingMessageSource.WatchEventType.DELETE),
                            e -> e.poller(
                                    Pollers.fixedRate(delayBetweenReads, delayBetweenReads.multipliedBy(2))))
                    .transform(org.springframework.integration.file.dsl.Files.toStringTransformer())
                    .channel(c -> c.flux("fileInboundFluxChannel"));

            IntegrationFlow flow = flowBuilder.get();

            flowRegistration = integrationFlowContext.registration(flow)
                    .id("scanner-" + System.identityHashCode(this))
                    .autoStartup(false)
                    .register();

            // The integration flow registers a FluxMessageChannel named "fileInboundFluxChannel".
            // We need to get this channel from the messaging system.
            // Since IntegrationFlowContext doesn't expose the channel directly,
            // we use the flow's internal channel by registering a listener.
            FluxMessageChannel filesChannel = new FluxMessageChannel();
            
            // Subscribe to the source flux and emit through the sink
            Flux<FileHistory> sourceFlux = IntegrationReactiveUtils.messageChannelToFlux(filesChannel)
                    .doOnSubscribe(s -> {
                        log.info("Starting integration flow for scanner at {}", folderPath);
                        if (flowRegistration != null) {
                            try {
                                // Delay start to let the channel settle
                                reactor.core.publisher.Mono.delay(Duration.ofSeconds(1))
                                        .subscribe(l -> flowRegistration.start());
                            } catch (Exception e) {
                                log.warn("Could not start flow registration immediately", e);
                            }
                        }
                    })
                    .replay(1)
                    .doOnNext(m -> log.debug("Received message: {}", m.getPayload()))
                    .map(m -> {
                        String s = (String) m.getPayload();
                        String hash = FileHash.hash(s);
                        String file = (String) m.getHeaders().get("file_relativePath");
                        log.debug("Processing file: {} with hash: {}", file, hash);
                        return new FileMetadata(file, s, hash);
                    })
                    .doOnNext(fm -> log.debug("FileMetadata created: {}", fm.url()))
                    .map(fileComparator(fileMetadataDatabase)::matches)
                    .doOnNext(fh -> log.debug("FileHistory created, hashMatches: {}", fh.hashMatches()))
                    .filter(fh -> {
                        boolean passes = !fh.hashMatches();
                        log.debug("Filter result for {}: {}", fh.currentFile().url(), passes);
                        return passes;
                    })
                    .doOnNext(fh -> {
                        log.debug("Saving to database: {}", fh.currentFile().url());
                        fileMetadataDatabase.save(fh.currentFile());
                    })
                    .share();

            // Subscribe to the source flux and emit through the sink
            sourceFlux.subscribe(sink::tryEmitNext);

            log.info("Scanner flow initialised for folder: {}", folderPath);

        } catch (Exception e) {
            log.error("Failed to initialise scanner flow for folder: {}", folderPath, e);
        }
    }

    /**
     * Create a FileComparator for the given database.
     */
    private FileComparator fileComparator(FileMetadataDatabase db) {
        return new FileComparator(db);
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
     * Emits all files from the target directory.
     * The watch service flow remains active for subsequent incremental updates.
     */
    public void resetToFullScan() {
        log.info("Resetting scanner for full scan at: {}", folderPath);
        // Complete the old sink and create a new one
        sink.tryEmitComplete();
        // Scan all current files and emit them
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

            FileComparator comparator = fileComparator(fileMetadataDatabase);

            List<Path> paths = Files.walk(folder)
                    .filter(Files::isRegularFile)
                    .toList();

            for (Path p : paths) {
                try {
                    String content = Files.readString(p);
                    String hash = FileHash.hash(content);
                    String relativePath = folder.relativize(p).toString();
                    String fullPath = p.toAbsolutePath().toString();

                    FileMetadata metadata = new FileMetadata(fullPath, content, hash);
                    FileHistory history = comparator.matches(metadata);

                    if (!history.hashMatches()) {
                        log.debug("Full scan - emitting new file: {}", fullPath);
                        fileMetadataDatabase.save(metadata);
                        sink.tryEmitNext(history);
                    } else {
                        log.debug("Full scan - skipping existing file: {}", fullPath);
                    }
                } catch (IOException e) {
                    log.warn("Failed to read file during full scan: {}", p, e);
                }
            }
        } catch (IOException e) {
            log.error("Failed to walk folder during full scan: {}", folderPath, e);
        }
    }

    /**
     * Destroy the integration flow registration and clean up resources.
     */
    public void destroy() {
        if (disposed) {
            return;
        }
        disposed = true;
        if (flowRegistration != null) {
            try {
                flowRegistration.destroy();
            } catch (Exception e) {
                log.warn("Error destroying flow registration for scanner at {}", folderPath, e);
            }
            flowRegistration = null;
        }
        sink.tryEmitComplete();
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

package com.hdekker.ai_workflow.adapter.outbound.file;

import java.nio.file.Path;
import java.time.Duration;

import org.springframework.stereotype.Component;

/**
 * Factory for creating {@link NativeFileWatcher} instances.
 * <p>
 * Encapsulates the adapter construction so that {@link com.hdekker.ai_workflow.application.pipeline.ScannerRegistry}
 * doesn't need to know the adapter's constructor details. The adapter is a pure
 * infrastructure component — it needs only the directory path and poll interval.
 */
@Component
public class FileSystemScannerAdapterFactory {

    /**
     * Create a new NativeFileWatcherAdapter for the given directory.
     *
     * @param folderPath        absolute path to watch
     * @param delayBetweenReads poll interval for the watch service
     * @return a new adapter instance
     */
    public NativeFileWatcher create(String folderPath, Duration delayBetweenReads) {
        return new NativeFileWatcher(Path.of(folderPath), delayBetweenReads);
    }
}

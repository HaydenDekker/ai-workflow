package com.hdekker.ai_workflow.files;

import java.io.IOException;
import java.nio.file.*;
import java.time.Duration;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hdekker.ai_workflow.files.FileHistory;
import com.hdekker.ai_workflow.files.FileMetadataStore;
import com.hdekker.ai_workflow.files.domain.FileMetadata;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

/**
 * A native NIO-based file watcher that emits file changes as reactive streams.
 * <p>
 * This class replaces Spring Integration's {@code FileReadingMessageSource} by
 * using {@link WatchService} directly. It supports CREATE, MODIFY, and DELETE events,
 * applies change detection via {@link FileComparator}, and persists metadata
 * to the configured database.
 * <p>
 * Lifecycle is managed externally: call {@link #start()} to begin watching
 * and {@link #stop()} to clean up resources.
 */
public class NativeFileWatcher {

	private static final Logger log = LoggerFactory.getLogger(NativeFileWatcher.class);

	private final Path directory;
	private final Duration pollInterval;
	private final FileMetadataStore fileMetadataStore;
	private final FileComparator fileComparator;

	private final Sinks.Many<FileHistory> sink;
	private WatchService watchService;
	private volatile boolean running = false;
	private volatile Thread watchThread;

	// Functional callbacks for metrics (replaced Micrometer types)
	private final Consumer<String> onDiscovery;
	private final Consumer<String> onUnchanged;
	private final Consumer<String> onFileCount;
	private final Consumer<FileHistory> emitCallback;

	/**
	 * Creates a new file watcher.
	 *
	 * @param directory                  absolute path to watch
	 * @param pollInterval               interval for polling (used as fallback)
	 * @param fileMetadataStore          metadata store for change detection
	 * @param onDiscovery                callback invoked when a new file is discovered
	 * @param onUnchanged                callback invoked when a file is unchanged
	 * @param onFileCount                callback invoked when file count changes
	 * @param emitCallback               callback invoked after each file emission
	 */
	public NativeFileWatcher(Path directory,
			Duration pollInterval,
			FileMetadataStore fileMetadataStore,
			Consumer<String> onDiscovery,
			Consumer<String> onUnchanged,
			Consumer<String> onFileCount,
			Consumer<FileHistory> emitCallback) {
		this.directory = directory.toAbsolutePath().normalize();
		this.pollInterval = pollInterval;
		this.fileMetadataStore = fileMetadataStore;
		this.fileComparator = new FileComparator(fileMetadataStore);
		this.sink = Sinks.many().multicast().directBestEffort();
		this.onDiscovery = onDiscovery;
		this.onUnchanged = onUnchanged;
		this.onFileCount = onFileCount;
		this.emitCallback = emitCallback;
	}

	/**
	 * Start watching the directory for file changes.
	 * <p>
	 * Registers a watch service for CREATE, MODIFY, and DELETE events in
	 * a background thread, then performs an initial full scan.
	 */
	public void start() {
		if (running) {
			log.warn("Watcher already running for: {}", directory);
			return;
		}

		try {
			watchService = FileSystems.getDefault().newWatchService();

			// Register watch dirs in a non-blocking way
			registerWatchDirs();
			running = true;
			watchThread = new Thread(this::watchLoop, "native-file-watcher");
			watchThread.setDaemon(true);
			watchThread.start();

			// Initial full scan
			log.info("Performing initial full scan of: {}", directory);
			scanAllFiles();
			log.info("File watcher started for: {}", directory);

		} catch (IOException e) {
			log.error("Failed to start watcher for: {}", directory, e);
			running = false;
		}
	}

	/**
	 * Register watch service on all directories. Called synchronously during start().
	 */
	private void registerWatchDirs() throws IOException {
		Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult preVisitDirectory(Path dir, java.nio.file.attribute.BasicFileAttributes attrs)
					throws IOException {
				dir.register(watchService,
						StandardWatchEventKinds.ENTRY_CREATE,
						StandardWatchEventKinds.ENTRY_MODIFY,
						StandardWatchEventKinds.ENTRY_DELETE);
				return FileVisitResult.CONTINUE;
			}
		});
	}

	/**
	 * The main watch loop that processes file system events.
	 */
	private void watchLoop() {
		while (running) {
			try {
				WatchKey key = watchService.poll(pollInterval.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
				if (key == null) {
					continue;
				}

				for (WatchEvent<?> event : key.pollEvents()) {
					WatchEvent.Kind<?> kind = event.kind();

					// Overflow event indicates events were lost
					if (kind == StandardWatchEventKinds.OVERFLOW) {
						continue;
					}

					@SuppressWarnings("unchecked")
					WatchEvent<Path> pathEvent = (WatchEvent<Path>) event;
					Path eventName = pathEvent.context();
					Path eventPath = directory.resolve(eventName);

					processEvent(kind, eventPath);
				}

				// Reset the key
				boolean valid = key.reset();
				if (!valid) {
					log.warn("Watch key no longer valid for: {}, skipping to next poll", directory);
				}

			} catch (java.nio.file.ClosedWatchServiceException e) {
				// Service was closed, stop watching
				break;
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				break;
			}
		}
	}

	/**
	 * Process a single file system event.
	 */
	private void processEvent(WatchEvent.Kind<?> kind, Path eventPath) {
		try {
			switch (kind.name()) {
				case "ENTRY_CREATE" -> {
					if (Files.isRegularFile(eventPath)) {
						// Small delay to ensure file is fully written
						Thread.sleep(100);
						emitFile(eventPath);
					}
				}
				case "ENTRY_MODIFY" -> {
					if (Files.isRegularFile(eventPath)) {
						// Small delay to ensure file is fully written
						Thread.sleep(100);
						emitFile(eventPath);
					}
				}
				case "ENTRY_DELETE" -> {
					log.debug("File deleted: {}", eventPath);
					// Note: We don't emit DELETE events as changes since
					// the content is no longer available. The file will
					// be re-created if it appears again.
					// Update file count callback
					if (onFileCount != null) {
						onFileCount.accept(directory.toString());
					}
				}
				default -> log.debug("Unknown event kind: {} for: {}", kind, eventPath);
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	/**
	 * Read a file and emit it through the sink if it's new or changed.
	 * Called during watch events (CREATE/MODIFY).
	 */
	private void emitFile(Path path) {
		try {
			String content = Files.readString(path);
			String hash = FileHash.hash(content);
			String relativePath = directory.relativize(path).toString().replace("\\", "/");
			FileMetadata metadata = new FileMetadata(relativePath, content, hash);
			FileHistory history = fileComparator.matches(metadata);

			if (!history.hashMatches()) {
				if (onDiscovery != null) {
					onDiscovery.accept(directory.toString());
				}
				log.debug("New or changed file: {}", relativePath);
				fileMetadataStore.save(metadata);
				sink.tryEmitNext(history);
			} else {
				if (onUnchanged != null) {
					onUnchanged.accept(directory.toString());
				}
				log.debug("Unchanged file (skipped): {}", relativePath);
			}
		} catch (IOException e) {
			log.warn("Failed to read file for event: {}", path, e);
		}

		// Update file count after any file event (creates, modifies)
		if (onFileCount != null) {
			onFileCount.accept(directory.toString());
		}

		// Invoke callback after emission
		if (emitCallback != null) {
			try {
				// Re-read to get a fresh history for the callback
				String content = Files.readString(path);
				String hash = FileHash.hash(content);
				String relativePath = directory.relativize(path).toString().replace("\\", "/");
				FileMetadata metadata = new FileMetadata(relativePath, content, hash);
				FileHistory history = fileComparator.matches(metadata);
				emitCallback.accept(history);
			} catch (IOException e) {
				log.warn("Failed to re-read file for callback: {}", path, e);
			}
		}
	}

	/**
	 * Counts the number of regular files in the watched directory.
	 * Called on every scan and on each file event to keep the gauge accurate.
	 */
	private long countFiles() {
		try {
			return Files.walk(directory)
					.filter(Files::isRegularFile)
					.count();
		} catch (IOException e) {
			return 0L;
		}
	}

	/**
	 * Scan all files in the directory and emit new ones.
	 * Called during initial startup (start()).
	 */
	private void scanAllFiles() throws IOException {
		Files.walk(directory)
				.filter(Files::isRegularFile)
				.forEach(p -> {
					try {
						String content = Files.readString(p);
						String hash = FileHash.hash(content);
						String relativePath = directory.relativize(p).toString().replace("\\", "/");
						FileMetadata metadata = new FileMetadata(relativePath, content, hash);
						FileHistory history = fileComparator.matches(metadata);

						if (!history.hashMatches()) {
							if (onDiscovery != null) {
								onDiscovery.accept(directory.toString());
							}
							log.debug("Scan - emitting new file: {}", relativePath);
							fileMetadataStore.save(metadata);
							sink.tryEmitNext(history);
						} else {
							if (onUnchanged != null) {
								onUnchanged.accept(directory.toString());
							}
							log.debug("Scan - skipping existing file: {}", relativePath);
						}
					} catch (IOException e) {
						log.warn("Failed to read file during scan: {}", p, e);
					}
				});

		// Update file count after full scan
		if (onFileCount != null) {
			onFileCount.accept(directory.toString());
		}
	}

	/**
	 * Stop the watcher and clean up resources.
	 */
	public void stop() {
		if (!running) {
			return;
		}
		running = false;
		sink.tryEmitComplete();

		// Wake up the watch service to unblock poll() if it's blocked
		if (watchService != null) {
			try {
				// WatchService doesn't have wakeup() - interrupt the thread instead
			if (watchThread != null) {
				watchThread.interrupt();
			}
			} catch (ClosedWatchServiceException e) {
				// Already closed, ignore
			}
		}

		if (watchThread != null) {
			watchThread.interrupt();
			try {
				watchThread.join(2000);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}

		if (watchService != null) {
			try {
				watchService.close();
			} catch (IOException e) {
				log.warn("Failed to close watch service", e);
			}
		}
		log.info("File watcher stopped for: {}", directory);
	}

	/**
	 * Check if the watcher is currently running.
	 */
	public boolean isRunning() {
		return running;
	}

	/**
	 * Get the flux of file change events.
	 */
	public Flux<FileHistory> flux() {
		return sink.asFlux().onBackpressureBuffer();
	}

	/**
	 * Emit a file history change directly. Used by the adapter for full scans.
	 */
	public void emit(FileHistory history) {
		sink.tryEmitNext(history);
	}
}

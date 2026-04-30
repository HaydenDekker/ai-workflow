package com.hdekker.ai_workflow.files;

import java.io.IOException;
import java.nio.file.*;
import java.time.Duration;
import java.time.LocalDateTime;
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
 * <p>
 * Emission behaviour:
 * <ul>
 *   <li>Consecutive emissions are throttled by at least {@code emissionDelay} seconds.</li>
 *   <li>Events arriving during the delay window are coalesced (only the latest file state is emitted).</li>
 *   <li>If an error occurs during scanning or event processing, the {@code onError} callback is invoked.</li>
 * </ul>
 * <p>
 * Lifecycle is managed externally: call {@link #start()} to begin watching
 * and {@link #stop()} to clean up resources.
 */
public class NativeFileWatcherAdapter {

	private static final Logger log = LoggerFactory.getLogger(NativeFileWatcherAdapter.class);

	private final Path directory;
	private final Duration pollInterval;
	private final Duration emissionDelay;
	private final FileMetadataStore fileMetadataStore;
	private final FileComparator fileComparator;

	private final Sinks.Many<FileHistory> sink;
	private WatchService watchService;
	private volatile boolean running = false;
	private volatile Thread watchThread;

	// Emission throttle state
	private volatile LocalDateTime lastEmissionTime;
	private volatile FileHistory latestBufferedHistory;

	// Tracks whether any files were buffered during the initial scan.
	// Used by the adapter to determine whether to transition to
	// EMITTING_UPDATES or stay IDLE after startup.
	private volatile boolean scanBufferedAnyFile = false;

	// Functional callbacks for metrics (replaced Micrometer types)
	private final Consumer<String> onDiscovery;
	private final Consumer<String> onUnchanged;
	private final Consumer<String> onFileCount;
	private final Consumer<FileHistory> emitCallback;
	private final Consumer<String> onFiltered; // called when hash filter rejects a file
	private final Consumer<String> onEmit; // called when a file is emitted (updates idle timer)

	// Error reporting callback (takes agentId + error message)
	private final String agentId;
	private final Consumer<String> onError;

	/**
	 * Creates a new file watcher.
	 *
	 * @param directory                  absolute path to watch
	 * @param pollInterval               interval for polling (used as fallback)
	 * @param emissionDelay              minimum interval between consecutive file emissions
	 * @param fileMetadataStore          metadata store for change detection
	 * @param onDiscovery                callback invoked when a new file is discovered
	 * @param onUnchanged                callback invoked when a file is unchanged
	 * @param onFileCount                callback invoked when file count changes
	 * @param emitCallback               callback invoked after each file emission
	 * @param onFiltered                 callback invoked when hash filter rejects a file
	 * @param onEmit                     callback invoked when a file is emitted (updates idle timer)
	 * @param agentId                    owning agent ID (for error reporting)
	 * @param onError                    callback invoked when an error occurs
	 */
	public NativeFileWatcherAdapter(Path directory,
			Duration pollInterval,
			Duration emissionDelay,
			FileMetadataStore fileMetadataStore,
			Consumer<String> onDiscovery,
			Consumer<String> onUnchanged,
			Consumer<String> onFileCount,
			Consumer<FileHistory> emitCallback,
			Consumer<String> onFiltered,
			Consumer<String> onEmit,
			String agentId,
			Consumer<String> onError) {
		this.directory = directory.toAbsolutePath().normalize();
		this.pollInterval = pollInterval;
		this.emissionDelay = emissionDelay;
		this.fileMetadataStore = fileMetadataStore;
		this.fileComparator = new FileComparator(fileMetadataStore);
		this.sink = Sinks.many().multicast().directBestEffort();
		this.onDiscovery = onDiscovery;
		this.onUnchanged = onUnchanged;
		this.onFileCount = onFileCount;
		this.emitCallback = emitCallback;
		this.onFiltered = onFiltered;
		this.onEmit = onEmit;
		this.agentId = agentId;
		this.onError = onError;
		this.lastEmissionTime = LocalDateTime.now();
	}

	/**
	 * Backward-compatible constructor for tests.
	 * <p>
	 * Uses zero emission delay and no error callback.
	 *
	 * @param directory                  absolute path to watch
	 * @param pollInterval               interval for polling (used as fallback)
	 * @param fileMetadataStore          metadata store for change detection
	 * @param onDiscovery                callback invoked when a new file is discovered
	 * @param onUnchanged                callback invoked when a file is unchanged
	 * @param onFileCount                callback invoked when file count changes
	 * @param emitCallback               callback invoked after each file emission
	 */
	public NativeFileWatcherAdapter(Path directory,
			Duration pollInterval,
			FileMetadataStore fileMetadataStore,
			Consumer<String> onDiscovery,
			Consumer<String> onUnchanged,
			Consumer<String> onFileCount,
			Consumer<FileHistory> emitCallback) {
		this(directory, pollInterval, Duration.ZERO, fileMetadataStore,
			onDiscovery, onUnchanged, onFileCount, emitCallback, null, null, null, null);
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
			if (onError != null) {
				onError.accept("Failed to start watcher: " + e.getMessage());
			}
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
		} catch (Exception e) {
			log.error("Error processing event {} for path {}: {}", kind, eventPath, e.getMessage());
			if (onError != null) {
				onError.accept("Error processing event " + kind + " for " + eventPath + ": " + e.getMessage());
			}
		}
	}

	/**
	 * Read a file and emit it through the sink if it's new or changed.
	 * Called during watch events (CREATE/MODIFY).
	 * <p>
	 * Applies emission delay throttling: if the delay has not elapsed since
	 * the last emission, the file history is buffered and the next emission
	 * will use the latest buffered state.
	 */
	private void emitFile(Path path) {
		FileHistory history = null;
		try {
			String content = Files.readString(path);
			String hash = FileHash.hash(content);
			String relativePath = directory.relativize(path).toString().replace("\\", "/");
			FileMetadata metadata = new FileMetadata(relativePath, content, hash);
			history = fileComparator.matches(metadata);

			if (!history.hashMatches()) {
				if (onDiscovery != null) {
					onDiscovery.accept(directory.toString());
				}
				log.debug("New or changed file: {}", relativePath);
				fileMetadataStore.save(metadata);
			} else {
				if (onUnchanged != null) {
					onUnchanged.accept(directory.toString());
				}
				// Notify registry that a file was filtered by the hash check
				if (onFiltered != null) {
					onFiltered.accept(directory.toString());
				}
				log.debug("Unchanged file (skipped): {}", relativePath);
			}
		} catch (IOException e) {
			log.warn("Failed to read file for event: {}", path, e);
			if (onError != null) {
				onError.accept("Failed to read file: " + e.getMessage());
			}
		}

		// Update file count after any file event (creates, modifies)
		if (onFileCount != null) {
			onFileCount.accept(directory.toString());
		}

		// Only emit if the file actually changed (hash mismatch)
		if (history != null && !history.hashMatches() && emitCallback != null) {
			// Apply emission delay throttling — only fire onEmit if actually emitted
			boolean emitted = tryEmitWithDelay(history);
			if (emitted) {
				// Notify registry that a file was emitted — updates idle timer
				// and transitions status to EMITTING_UPDATES so the idle checker
				// knows the scanner is actively processing files.
				if (onEmit != null) {
					onEmit.accept(directory.toString());
				}
			}
		}
	}

	/**
	 * Attempt to emit a file history through the sink, respecting the emission delay.
	 * <p>
	 * If the delay has not elapsed since the last emission, the history is buffered
	 * and will be emitted when the delay elapses (or on the next call).
	 *
	 * @param history the file history to emit
	 * @return true if the file was actually emitted through the sink, false if buffered
	 */
	private boolean tryEmitWithDelay(FileHistory history) {
		if (history == null) {
			return false;
		}

		LocalDateTime now = LocalDateTime.now();
		Duration elapsed = Duration.between(lastEmissionTime, now);

		// Coalesce: always update the buffered history
		latestBufferedHistory = history;

		if (emissionDelay == null || emissionDelay.isZero() || emissionDelay.isNegative()) {
			// No delay configured — emit immediately
			sink.tryEmitNext(history);
			lastEmissionTime = now;
			scanBufferedAnyFile = true;
			return true;
		} else if (elapsed.getSeconds() >= emissionDelay.getSeconds()) {
			// Delay has elapsed — emit and record time
			sink.tryEmitNext(history);
			lastEmissionTime = now;
			scanBufferedAnyFile = true;
			return true;
		}
		// else: delay not elapsed, history is buffered for later emission.
		// Do NOT update lastEmissionTime or scanBufferedAnyFile here — the timer
		// must keep running from the original emission time, and the flag must only
		// reflect actual emissions, not buffered-but-not-yet-emitted files.
		return false;
	}

	/**
	 * Flush any buffered history if the emission delay has elapsed.
	 */
	public void flushBufferedEmission() {
		if (latestBufferedHistory == null) {
			return;
		}

		LocalDateTime now = LocalDateTime.now();
		Duration elapsed = Duration.between(lastEmissionTime, now);

		if (elapsed.getSeconds() >= emissionDelay.getSeconds()) {
			sink.tryEmitNext(latestBufferedHistory);
			lastEmissionTime = now;
			latestBufferedHistory = null;
			// Do NOT reset scanBufferedAnyFile here — the adapter checks this flag
			// to decide whether to transition to EMITTING_UPDATES. Resetting it
			// after flush would cause the adapter to stay IDLE even though files
			// were buffered and emitted during the scan.
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
							// Apply emission delay throttling during scan
							tryEmitWithDelay(history);
						} else {
							if (onUnchanged != null) {
								onUnchanged.accept(directory.toString());
							}
							// Notify registry that a file was filtered by the hash check
							if (onFiltered != null) {
								onFiltered.accept(directory.toString());
							}
							log.debug("Scan - skipping existing file: {}", relativePath);
						}
					} catch (IOException e) {
						log.warn("Failed to read file during scan: {}", p, e);
						if (onError != null) {
							onError.accept("Failed to read file during scan: " + e.getMessage());
						}
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
	 * <p>
	 * Also respects the emission delay throttle.
	 */
	public void emit(FileHistory history) {
		tryEmitWithDelay(history);
	}

	/**
	 * Get the agent ID associated with this watcher.
	 */
	public String getAgentId() {
		return agentId;
	}

	/**
	 * Get the last emission time (for testing).
	 */
	LocalDateTime getLastEmissionTime() {
		return lastEmissionTime;
	}

	/**
	 * Check whether any files were buffered during the initial scan.
	 * <p>
	 * Used by the adapter to determine whether to transition to
	 * {@code EMITTING_UPDATES} or stay {@code IDLE} after startup.
	 *
	 * @return true if at least one file was buffered during the initial scan
	 */
	public boolean scanBufferedAnyFile() {
		return scanBufferedAnyFile;
	}
}

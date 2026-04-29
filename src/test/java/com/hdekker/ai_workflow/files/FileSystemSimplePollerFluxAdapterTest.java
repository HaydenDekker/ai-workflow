package com.hdekker.ai_workflow.files;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.hdekker.ai_workflow.files.domain.FileMetadata;
import com.hdekker.ai_workflow.TestConstants;
import com.hdekker.ai_workflow.usecases.ScannerObserverUseCase;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;

public class FileSystemSimplePollerFluxAdapterTest {

	@TempDir
	Path tempDirectory;

	private NativeFileWatcher fileWatcher;

	@AfterEach
	void tearDown() {
		if (fileWatcher != null) {
			fileWatcher.stop();
			fileWatcher = null;
		}
	}

	static class InMemoryFileMetaDatabase implements FileMetadataStore {
		List<FileMetadata> stored = new ArrayList<>();

		@Override
		public Optional<FileMetadata> findById(String url) {
			return stored.stream()
					.filter(fm -> fm.url().equals(url))
					.findFirst();
		}

		@Override
		public void save(FileMetadata file) {
			stored.add(file);
		}
	}

	public Flux<FileHistory> createFluxWithNativeWatcher(File folder, FileMetadataStore database) {
		Duration pollInterval = Duration.ofMillis(500);
		ScannerObserverUseCase observer = new ScannerObserverUseCase();
		NativeFileWatcher watcher = new NativeFileWatcher(
				folder.toPath(), pollInterval, database,
				agentId -> observer.recordDiscovery(agentId),
				agentId -> observer.recordUnchanged(agentId),
				agentId -> observer.updateFileCount(agentId, countFiles(folder)),
				history -> {}); // no-op callback for tests
		watcher.start();
		return watcher.flux();
	}

	private long countFiles(File folder) {
		try {
			return java.nio.file.Files.walk(folder.toPath())
					.filter(java.nio.file.Files::isRegularFile)
					.count();
		} catch (Exception e) {
			return 0L;
		}
	}

	@Test
	public void canCaptureFileCreationWithNativeWatcher() throws IOException, InterruptedException {
		InMemoryFileMetaDatabase database = new InMemoryFileMetaDatabase();
		File folder = tempDirectory.toFile();

		// Track emissions with a latch
		CountDownLatch latch = new CountDownLatch(1);
		FileHistory[] received = new FileHistory[1];

		Flux<FileHistory> flux = createFluxWithNativeWatcher(folder, database);

		// The initial scan should pick up no files since the dir is empty
		assertThat(database.stored).isEmpty();

		// Create a file - the watch service should detect this
		Path testFile = tempDirectory.resolve(TestConstants.FILE_POOR_SOLID_COMPLIANCE);
		Files.copy(Path.of(TestConstants.getTestFilePath(TestConstants.FILE_POOR_SOLID_COMPLIANCE)), testFile);

		// Subscribe and wait for emission
		flux.subscribe(fh -> {
			received[0] = fh;
			latch.countDown();
		});

		// Wait up to 5 seconds for the file event
		boolean completed = latch.await(5, TimeUnit.SECONDS);
		assertThat(completed).as("Expected file creation event within 5 seconds").isTrue();

		assertThat(received[0].currentFile().url()).contains(TestConstants.FILE_POOR_SOLID_COMPLIANCE);
		assertThat(received[0].previousFile()).isEmpty();
	}

	@Test
	public void canDetectFileModificationWithNativeWatcher() throws IOException, InterruptedException {
		InMemoryFileMetaDatabase database = new InMemoryFileMetaDatabase();
		File folder = tempDirectory.toFile();

		CountDownLatch latch1 = new CountDownLatch(1);
		CountDownLatch latch2 = new CountDownLatch(1);
		FileHistory[] first = new FileHistory[1];
		FileHistory[] second = new FileHistory[1];
		int[] count = new int[1];

		Flux<FileHistory> flux = createFluxWithNativeWatcher(folder, database);

		Path testFile = tempDirectory.resolve("test2.txt");

		// Create initial file
		Files.writeString(testFile, "initial content");

		// Subscribe and count emissions
		flux.subscribe(fh -> {
			if (count[0] == 0) {
				first[0] = fh;
				latch1.countDown();
			} else {
				second[0] = fh;
				latch2.countDown();
			}
			count[0]++;
		});

		// Wait for initial file emission
		assertThat(latch1.await(5, TimeUnit.SECONDS))
				.as("Expected initial file creation event").isTrue();

		assertThat(first[0].currentFile().body()).isEqualTo("initial content");
		assertThat(first[0].previousFile()).isEmpty();

		// Modify the file
		Files.writeString(testFile, "modified content");

		// Wait for modification event
		assertThat(latch2.await(5, TimeUnit.SECONDS))
				.as("Expected file modification event").isTrue();

		assertThat(second[0].currentFile().body()).isEqualTo("modified content");
		assertThat(second[0].previousFile()).isPresent();
		assertThat(second[0].previousFile().get().body()).isEqualTo("initial content");
	}

	@Test
	public void skipsUnchangedFilesWithNativeWatcher() throws IOException, InterruptedException {
		InMemoryFileMetaDatabase database = new InMemoryFileMetaDatabase();
		File folder = tempDirectory.toFile();

		CountDownLatch latch = new CountDownLatch(1);
		int[] count = new int[1];

		Flux<FileHistory> flux = createFluxWithNativeWatcher(folder, database);

		// Subscribe and count emissions
		flux.subscribe(fh -> {
			count[0]++;
			latch.countDown();
		});

		// Create file AFTER watcher started - watch service will detect it
		Path testFile = tempDirectory.resolve("test3.txt");
		Files.writeString(testFile, "unchanged content");

		// Wait for watch service to detect and emit
		assertThat(latch.await(5, TimeUnit.SECONDS))
				.as("Expected file creation event via watch service").isTrue();

		// File was stored in database during emission
		assertThat(database.stored).hasSize(1);

		// Wait extra time to ensure no additional emissions for unchanged file
		Thread.sleep(3000);

		// Should only have received the initial file event, not repeated events
		assertThat(count[0]).isEqualTo(1);
	}
}

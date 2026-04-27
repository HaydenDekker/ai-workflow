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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.hdekker.ai_workflow.files.domain.FileMetadata;
import com.hdekker.ai_workflow.TestConstants;

import reactor.core.publisher.Flux;
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
		Duration pollInterval = Duration.ofMillis(2000);
		NativeFileWatcher watcher = new NativeFileWatcher(folder.toPath(), pollInterval, database);
		watcher.start();
		return watcher.flux();
	}

	@Test
	public void canCaptureFileCreationWithNativeWatcher() throws IOException, InterruptedException {
		InMemoryFileMetaDatabase database = new InMemoryFileMetaDatabase();
		File folder = tempDirectory.toFile();

		Flux<FileHistory> flux = createFluxWithNativeWatcher(folder, database);

		// The initial scan should pick up no files since the dir is empty
		assertThat(database.stored).isEmpty();

		// Create a file - the watch service should detect this
		Path testFile = tempDirectory.resolve(TestConstants.FILE_POOR_SOLID_COMPLIANCE);
		Files.copy(Path.of(TestConstants.getTestFilePath(TestConstants.FILE_POOR_SOLID_COMPLIANCE)), testFile);

		// Wait for the watch service to process the event
		Thread.sleep(2500);

		StepVerifier.create(flux.take(1))
				.consumeNextWith(fh -> {
					assertThat(fh.currentFile().url()).contains(TestConstants.FILE_POOR_SOLID_COMPLIANCE);
					assertThat(fh.previousFile()).isEmpty();
				})
				.verifyComplete();
	}

	@Test
	public void canDetectFileModificationWithNativeWatcher() throws IOException, InterruptedException {
		InMemoryFileMetaDatabase database = new InMemoryFileMetaDatabase();
		File folder = tempDirectory.toFile();

		Flux<FileHistory> flux = createFluxWithNativeWatcher(folder, database);

		Path testFile = tempDirectory.resolve("test2.txt");

		Files.writeString(testFile, "initial content");
		Thread.sleep(2500);

		StepVerifier.create(flux.take(1))
				.consumeNextWith(fh -> {
					assertThat(fh.currentFile().body()).isEqualTo("initial content");
					assertThat(fh.previousFile()).isEmpty();
				})
				.verifyComplete();

		// Modify the file
		Files.writeString(testFile, "modified content");
		Thread.sleep(2500);

		StepVerifier.create(flux.take(1))
				.consumeNextWith(fh -> {
					assertThat(fh.currentFile().body()).isEqualTo("modified content");
					assertThat(fh.previousFile()).isPresent();
					assertThat(fh.previousFile().get().body()).isEqualTo("initial content");
				})
				.verifyComplete();
	}

	@Test
	public void skipsUnchangedFilesWithNativeWatcher() throws IOException, InterruptedException {
		InMemoryFileMetaDatabase database = new InMemoryFileMetaDatabase();
		File folder = tempDirectory.toFile();

		Flux<FileHistory> flux = createFluxWithNativeWatcher(folder, database);

		Path testFile = tempDirectory.resolve("test3.txt");
		Files.writeString(testFile, "unchanged content");
		Thread.sleep(2500);

		StepVerifier.create(flux.take(1))
				.consumeNextWith(fh -> {
					assertThat(fh.currentFile().body()).isEqualTo("unchanged content");
				})
				.verifyComplete();

		// Wait to confirm no more emissions for unchanged file
		Thread.sleep(4000);

		Flux<FileHistory> testFlux = flux.timeout(Duration.ofSeconds(3));

		StepVerifier.create(testFlux.take(1))
				.verifyErrorSatisfies(error -> {
					assertThat(error).isInstanceOf(java.util.concurrent.TimeoutException.class);
				});
	}
}

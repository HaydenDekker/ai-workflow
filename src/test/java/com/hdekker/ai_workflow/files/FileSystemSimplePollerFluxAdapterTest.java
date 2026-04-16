package com.hdekker.ai_workflow.files;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.integration.file.DefaultDirectoryScanner;
import org.springframework.integration.file.inbound.FileReadingMessageSource;
import org.springframework.integration.util.IntegrationReactiveUtils;
import org.springframework.messaging.Message;

import com.hdekker.ai_workflow.files.domain.FileMetadata;
import com.hdekker.ai_workflow.TestFiles;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

public class FileSystemSimplePollerFluxAdapterTest {
	
	@TempDir
	Path tempDirectory;
	
	static class InMemoryFileMetaDatabase implements FileMetaDatabaseSearcher {
		List<FileMetadata> stored = new ArrayList<>();
		
		@Override
		public Optional<FileMetadata> findById(String url) {
			return stored.stream()
					.filter(fm -> fm.url().equals(url))
					.findFirst();
		}
		
		public void save(FileMetadata file) {
			stored.add(file);
		}
	}
	
	public Flux<FileHistory> createFluxWithPoller(File folder, FileMetaDatabaseSearcher database) {
		FileReadingMessageSource source = new FileReadingMessageSource();
		source.setDirectory(folder);
		DefaultDirectoryScanner scanner = new DefaultDirectoryScanner();
		scanner.setFilter(files -> Arrays.asList(files));
		source.setScanner(scanner);
		
		FileComparator fileComparator = new FileComparator(database);
		
		return IntegrationReactiveUtils.messageSourceToFlux(source)
				.map(Message::getPayload)
				.map(file -> {
					try {
						String content = java.nio.file.Files.readString(file.toPath());
						String hash = FileHash.hash(content);
						String relativePath = folder.toPath().relativize(file.toPath()).toString().replace("\\", "/");
						return new FileMetadata(relativePath, content, hash);
					} catch (IOException e) {
						throw new RuntimeException(e);
					}
				})
				.map(fileComparator::matches)
				.filter(fh -> !fh.hashMatches())
				.onBackpressureBuffer();
	}
	
	public Flux<FileHistory> createFluxWithWatchService(File folder, FileMetaDatabaseSearcher database) {
		FileReadingMessageSource source = new FileReadingMessageSource();
		source.setDirectory(folder);
		DefaultDirectoryScanner scanner = new DefaultDirectoryScanner();
		scanner.setFilter(files -> Arrays.asList(files));
		source.setScanner(scanner);
		source.setUseWatchService(true);
		source.setWatchEvents(
				FileReadingMessageSource.WatchEventType.CREATE,
				FileReadingMessageSource.WatchEventType.MODIFY,
				FileReadingMessageSource.WatchEventType.DELETE);
		
		FileComparator fileComparator = new FileComparator(database);
		
		return IntegrationReactiveUtils.messageSourceToFlux(source)
				.map(Message::getPayload)
				.map(file -> {
					try {
						String content = java.nio.file.Files.readString(file.toPath());
						String hash = FileHash.hash(content);
						String relativePath = folder.toPath().relativize(file.toPath()).toString().replace("\\", "/");
						return new FileMetadata(relativePath, content, hash);
					} catch (IOException e) {
						throw new RuntimeException(e);
					}
				})
				.map(fileComparator::matches)
				.filter(fh -> !fh.hashMatches())
				.onBackpressureBuffer();
	}
	
	@Test
	public void canCaptureFileCreationWithSimplePollerFlux() throws IOException, InterruptedException {
		InMemoryFileMetaDatabase database = new InMemoryFileMetaDatabase();
		File folder = tempDirectory.toFile();
		
		Flux<FileHistory> flux = createFluxWithPoller(folder, database);
		
		Path testFile = tempDirectory.resolve(TestFiles.FILE_POOR_SOLID_COMPLIANCE);
		java.nio.file.Files.copy(Path.of(TestFiles.getTestFilePath(TestFiles.FILE_POOR_SOLID_COMPLIANCE)), testFile);
		Thread.sleep(100);
		
		StepVerifier.create(flux.take(1))
				.consumeNextWith(fh -> {
					assertThat(fh.currentFile().url()).contains(TestFiles.FILE_POOR_SOLID_COMPLIANCE);
					assertThat(fh.previousFile()).isEmpty();
				})
				.verifyComplete();
	}
	
	@Test
	public void canDetectFileModificationWithPoller() throws IOException, InterruptedException {
		InMemoryFileMetaDatabase database = new InMemoryFileMetaDatabase();
		File folder = tempDirectory.toFile();
		
		Flux<FileHistory> flux = createFluxWithPoller(folder, database);
		
		Path testFile = tempDirectory.resolve("test2.txt");
		
		java.nio.file.Files.writeString(testFile, "initial content");
		Thread.sleep(100);
		
		Mono<FileHistory> firstMono = flux.take(1).next();
		FileHistory first = firstMono.block(Duration.ofSeconds(10));
		
		assertThat(first).isNotNull();
		assertThat(first.currentFile().body()).isEqualTo("initial content");
		assertThat(first.previousFile()).isEmpty();
		
		database.save(first.currentFile());
		
		java.nio.file.Files.writeString(testFile, "modified content");
		Thread.sleep(100);
		
		StepVerifier.create(flux.take(1))
				.consumeNextWith(fh -> {
					assertThat(fh.currentFile().body()).isEqualTo("modified content");
					assertThat(fh.previousFile()).isPresent();
					assertThat(fh.previousFile().get().body()).isEqualTo("initial content");
				})
				.verifyComplete();
	}
	
	@Test
	public void skipsUnchangedFilesWithPoller() throws IOException, InterruptedException {
		InMemoryFileMetaDatabase database = new InMemoryFileMetaDatabase();
		File folder = tempDirectory.toFile();
		
		Flux<FileHistory> flux = createFluxWithPoller(folder, database);
		
		Path testFile = tempDirectory.resolve("test3.txt");
		java.nio.file.Files.writeString(testFile, "unchanged content");
		Thread.sleep(100);
		
		Mono<FileHistory> firstMono = flux.take(1).next();
		FileHistory first = firstMono.block(Duration.ofSeconds(10));
		
		assertThat(first).isNotNull();
		assertThat(first.currentFile().body()).isEqualTo("unchanged content");
		database.save(first.currentFile());
		
		Thread.sleep(100);
		
		Flux<FileHistory> testFlux = flux.timeout(Duration.ofSeconds(2));
		
		StepVerifier.create(testFlux.take(1))
				.verifyErrorSatisfies(error -> {
					assertThat(error).isInstanceOf(java.util.concurrent.TimeoutException.class);
				});
	}
	
	@Test
	public void canCaptureFileCreationWithWatchService() throws IOException, InterruptedException {
		InMemoryFileMetaDatabase database = new InMemoryFileMetaDatabase();
		File folder = tempDirectory.toFile();
		
		Flux<FileHistory> flux = createFluxWithWatchService(folder, database);
		
		Path testFile = tempDirectory.resolve("watch_test.txt");
		java.nio.file.Files.writeString(testFile, "watch service test content");
		Thread.sleep(200);
		
		StepVerifier.create(flux.take(1))
				.consumeNextWith(fh -> {
					assertThat(fh.currentFile().body()).isEqualTo("watch service test content");
					assertThat(fh.previousFile()).isEmpty();
				})
				.verifyComplete();
	}
	
	@Test
	public void canDetectFileModificationWithWatchService() throws IOException, InterruptedException {
		InMemoryFileMetaDatabase database = new InMemoryFileMetaDatabase();
		File folder = tempDirectory.toFile();
		
		Flux<FileHistory> flux = createFluxWithWatchService(folder, database);
		
		Path testFile = tempDirectory.resolve("watch_modify.txt");
		
		java.nio.file.Files.writeString(testFile, "initial watch content");
		Thread.sleep(200);
		
		Mono<FileHistory> firstMono = flux.take(1).next();
		FileHistory first = firstMono.block(Duration.ofSeconds(10));
		
		assertThat(first).isNotNull();
		assertThat(first.currentFile().body()).isEqualTo("initial watch content");
		assertThat(first.previousFile()).isEmpty();
		
		database.save(first.currentFile());
		
		java.nio.file.Files.writeString(testFile, "modified watch content");
		Thread.sleep(200);
		
		StepVerifier.create(flux.take(1))
				.consumeNextWith(fh -> {
					assertThat(fh.currentFile().body()).isEqualTo("modified watch content");
					assertThat(fh.previousFile()).isPresent();
					assertThat(fh.previousFile().get().body()).isEqualTo("initial watch content");
				})
				.verifyComplete();
	}

}

package com.hdekker.ai_workflow.files;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Scanner;


import org.junit.jupiter.api.Test;

import com.hdekker.ai_workflow.domain.file.FileMetadata;

public class FileUpdatedFilterTest {

	public String getString(InputStream is) {
		String isString;
		try (Scanner scanner = new Scanner(is, StandardCharsets.UTF_8).useDelimiter("\\A")) {
			isString = scanner.hasNext() ? scanner.next() : "";
		}
		return isString;
	}

	@Test
	public void givenNewFile_ExpectFileEmitted() {

		byte[] stubFileData1 = { 0x23, 0x45 };
		byte[] stubFileData2 = { 0x23, 0x46 };
		String dummyURL = "/happy-days";

		InputStream data1IS = new ByteArrayInputStream(stubFileData1);
		InputStream data2IS = new ByteArrayInputStream(stubFileData2);

		String previousFileHash = FileHash.hash(getString(data1IS));
		String currentFileHash = FileHash.hash(getString(data2IS));

		FileMetadataStore searcher = new FileMetadataStore() {
			@Override
			public Optional<FileMetadata> findById(String url) {
				return Optional.of(new FileMetadata(url, "", previousFileHash));
			}

			@Override
			public void save(FileMetadata file) {
				// No-op for test
			}
		};

		FileComparator fc = new FileComparator(searcher);
		FileHistory optUpdated = fc.matches(new FileMetadata(dummyURL, new String(stubFileData1), currentFileHash));
		assertThat(optUpdated.previousFile()).isPresent();

	}

}

package com.hdekker.ai_workflow.files;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

public class FileHashTest {

	@Test
	public void givenFile_ExpectHash() {
		
		String file = "Hello Test File Here.";
		String file2 = "Hello File 2 here";
		
		FileHash fh = new FileHash();
		String hash = fh.hash(file);
		String hash2 = fh.hash(file);
		
		assertThat(hash).isEqualTo(hash2);
		
		String hash3 = fh.hash(file2);
		assertThat(hash3).isNotEqualTo(hash);
		
	}
	
}

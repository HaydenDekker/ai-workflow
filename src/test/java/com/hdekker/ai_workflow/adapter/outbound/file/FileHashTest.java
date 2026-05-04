package com.hdekker.ai_workflow.adapter.outbound.file;

import static org.assertj.core.api.Assertions.assertThat;


import org.junit.jupiter.api.Test;

import com.hdekker.ai_workflow.domain.shared.FileHash;

public class FileHashTest {

	@Test
	public void givenFile_ExpectHash() {
		
		String file = "Hello Test File Here.";
		String file2 = "Hello File 2 here";
		
		String hash = FileHash.hash(file);
		String hash2 = FileHash.hash(file);
		
		assertThat(hash).isEqualTo(hash2);
		
		String hash3 = FileHash.hash(file2);
		assertThat(hash3).isNotEqualTo(hash);
		
	}
	
}

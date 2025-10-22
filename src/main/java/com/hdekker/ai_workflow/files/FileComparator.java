package com.hdekker.ai_workflow.files;

import java.util.Optional;

import com.hdekker.ai_workflow.files.domain.FileMetadata;

/**
 *  To access the local file system database
 *  and compare previous to new.
 * 
 */
public class FileComparator implements InputStreamHashChangedMatcher{
	
	FileMetaDatabaseSearcher repository;
	
	public FileComparator(FileMetaDatabaseSearcher repository){
		this.repository = repository;
	}

	@Override
	public FileHistory matches(FileMetadata file) {
		
		Optional<FileMetadata> previousFile = repository.findById(file.url());
		return new FileHistory(file,
					previousFile);
		
	}

}

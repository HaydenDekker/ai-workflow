package com.hdekker.ai_workflow.database.filemetadata;

import java.util.Optional;

import com.hdekker.ai_workflow.domain.file.FileMetadata;
import com.hdekker.ai_workflow.files.FileMetaDatabaseSearcher;
import com.hdekker.ai_workflow.files.FileMetadataStore;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class FileMetadataDatabase implements FileMetaDatabaseSearcher, FileMetadataStore {
	
	@Autowired
	FileMetaRepository fileMetaRepository;

	@Override
	public Optional<FileMetadata> findById(String url) {
		return fileMetaRepository.findById(url)
					.map(e->new FileMetadata(e.url, "", e.hash));
	}

	public void save(FileMetadata file) {
		
		FileMetadataEntity entity = new FileMetadataEntity();
		entity.setHash(file.hash());
		entity.setUrl(file.url());
		fileMetaRepository.save(entity);
	}
	
	

}

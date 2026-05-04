package com.hdekker.ai_workflow.adapter.outbound.persistence.filemetadata;

import java.util.Optional;

import com.hdekker.ai_workflow.adapter.outbound.file.FileMetaDatabaseSearcher;
import com.hdekker.ai_workflow.adapter.outbound.file.FileMetadataStoreAdapter;
import com.hdekker.ai_workflow.domain.file.FileMetadata;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class FileMetadataDatabaseAdapter implements FileMetaDatabaseSearcher, FileMetadataStoreAdapter {
	
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

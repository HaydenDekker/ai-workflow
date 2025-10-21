package com.hdekker.ai_workflow.database.filemetadata;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.hdekker.ai_workflow.files.FileMetaDatabaseSearcher;
import com.hdekker.ai_workflow.files.domain.FileMetadata;

@Service
public class FileMetadataDatabase implements FileMetaDatabaseSearcher{
	
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

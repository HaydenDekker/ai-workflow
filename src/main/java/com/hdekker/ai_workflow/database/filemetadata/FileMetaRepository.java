package com.hdekker.ai_workflow.database.filemetadata;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FileMetaRepository extends JpaRepository<FileMetadataEntity, String>{

}

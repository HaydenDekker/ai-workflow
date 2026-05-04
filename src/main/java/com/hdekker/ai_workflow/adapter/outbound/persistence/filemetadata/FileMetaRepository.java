package com.hdekker.ai_workflow.adapter.outbound.persistence.filemetadata;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FileMetaRepository extends JpaRepository<FileMetadataEntity, String>{

}

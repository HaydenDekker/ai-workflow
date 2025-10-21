package com.hdekker.ai_workflow.database.solid;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SolidComplianceRepository extends JpaRepository<SolidComplianceEntity, String>{
	List<SolidComplianceEntity> findAllByFileHash(String fileHash);
}

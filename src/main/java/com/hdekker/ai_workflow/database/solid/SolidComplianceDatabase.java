package com.hdekker.ai_workflow.database.solid;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.hdekker.ai_workflow.llm.output.SOLIDCompliance;

@Component
public class SolidComplianceDatabase {

	@Autowired
	SolidComplianceRepository solidComplianceRepository;
	
	public void save(SOLIDCompliance solidCompliance, String fileHash) {
		
		SolidComplianceEntity entity = new SolidComplianceEntity();
		entity.setFileHash(fileHash);
		entity.setClassName(solidCompliance.className());
		entity.setCompliance(solidCompliance.compliance());
		entity.setPrinciple(solidCompliance.principle());
		entity.setReason(solidCompliance.reason());
		entity.setTask(solidCompliance.task());
		solidComplianceRepository.save(entity);
		
	}

	public List<SOLIDCompliance> findAll() {
		return solidComplianceRepository.findAll()
					.stream()
					.map(sce->new SOLIDCompliance(
							sce.className,
							sce.compliance,
							sce.principle,
							sce.task,
							sce.reason))
					.toList();
	}
	
}

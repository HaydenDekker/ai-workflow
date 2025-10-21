package com.hdekker.ai_workflow.reports;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hdekker.ai_workflow.database.solid.SolidComplianceDatabase;
import com.hdekker.ai_workflow.llm.output.SOLIDCompliance;

import reactor.core.publisher.Flux;

@RestController
public class ReportRestController {
	
	@Autowired
	SolidComplianceDatabase solidComplianceDatabase;
	
	@GetMapping(path = "/solid-compliance-report")
	public Flux<SOLIDCompliance> complianceReport(){
		return Flux.fromStream(solidComplianceDatabase.findAll().stream());
	}

}

package com.hdekker.ai_workflow.pipeline.domain;

/**
 *  To capture configuration of a prompt for
 *  a pipeline
 * 
 */
public record PipelinePrompt(
		String event,
		String title,
		String body,
		String outputStructure
		) {

}

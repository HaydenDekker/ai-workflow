package com.hdekker.ai_workflow.pipeline;

public interface Enrichable<T> {
	T enrich(T input);
}

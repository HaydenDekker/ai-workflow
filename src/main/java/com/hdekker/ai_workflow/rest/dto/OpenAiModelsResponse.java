package com.hdekker.ai_workflow.rest.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO for OpenAI-compatible /v1/models API response.
 * Used for health checking LLM endpoints.
 */
public record OpenAiModelsResponse(
    @JsonProperty("object")
    String object,
    
    @JsonProperty("data")
    List<OpenAiModel> data
) {
    
    /**
     * Nested DTO for individual model information.
     */
    public record OpenAiModel(
        @JsonProperty("id")
        String id,
        
        @JsonProperty("aliases")
        List<String> aliases,
        
        @JsonProperty("tags")
        List<String> tags,
        
        @JsonProperty("object")
        String object,
        
        @JsonProperty("created")
        Long created,
        
        @JsonProperty("owned_by")
        String ownedBy,
        
        @JsonProperty("meta")
        Object meta
    ) {}
}

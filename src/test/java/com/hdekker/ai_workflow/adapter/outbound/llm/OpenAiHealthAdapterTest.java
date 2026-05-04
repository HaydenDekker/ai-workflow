package com.hdekker.ai_workflow.adapter.outbound.llm;

import static org.junit.jupiter.api.Assertions.*;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.hdekker.ai_workflow.adapter.inbound.rest.dto.AdapterStatus;
import com.hdekker.ai_workflow.adapter.inbound.rest.dto.LLMStatus;

/**
 * Tests for OpenAiHealthAdapter.
 * 
 * Note: These tests verify the adapter structure and error handling.
 * Actual endpoint connectivity tests require a running OpenAI-compatible server.
 */
class OpenAiHealthAdapterTest {
    
    private OpenAiHealthAdapter adapter;
    
    @BeforeEach
    void setUp() {
        adapter = new OpenAiHealthAdapter(5000);
    }
    
    @Test
    void adapterCreatedSuccessfully() {
        assertNotNull(adapter);
    }
    
    @Test
    void checkHealth_unavailableEndpoint_returnsDownStatus() {
        String endpoint = "http://localhost:19999";
        String configuredModel = "test-model";
        
        LLMStatus status = adapter.checkHealth(endpoint, configuredModel).block();
        
        assertNotNull(status);
        assertEquals(AdapterStatus.DOWN, status.status());
        assertEquals(endpoint, status.endpoint());
        assertEquals(configuredModel, status.configuredModel());
        assertEquals(0, status.modelCount());
        assertNotNull(status.errorMessage());
    }
    
    @Test
    void checkHealth_timeout_returnsDownStatus() {
        String endpoint = "http://nonexistent-host-12345:8080";
        String configuredModel = "test-model";
        
        OpenAiHealthAdapter fastAdapter = new OpenAiHealthAdapter(500);
        
        LLMStatus status = fastAdapter.checkHealth(endpoint, configuredModel).block();
        
        assertNotNull(status);
        assertEquals(AdapterStatus.DOWN, status.status());
        assertTrue(status.errorMessage().contains("Timeout") || 
                   status.errorMessage().contains("nonexistent") ||
                   status.errorMessage().contains("Connection"));
    }
}

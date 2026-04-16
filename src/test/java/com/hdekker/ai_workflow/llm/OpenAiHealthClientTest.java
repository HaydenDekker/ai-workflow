package com.hdekker.ai_workflow.llm;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Tests for OpenAiHealthClient.
 * 
 * Note: These tests verify the client can be instantiated.
 * Actual endpoint connectivity tests require a running OpenAI-compatible server.
 */
class OpenAiHealthClientTest {
    
    private OpenAiHealthClient client;
    
    @BeforeEach
    void setUp() {
        client = new OpenAiHealthClient("http://localhost:8080", 5000);
    }
    
    @Test
    void clientCreatedSuccessfully() {
        assertNotNull(client);
    }
}

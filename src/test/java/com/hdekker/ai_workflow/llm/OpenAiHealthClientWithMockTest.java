package com.hdekker.ai_workflow.llm;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Tests for OpenAiHealthClient with manual MockRestServiceServer setup.
 * Tests the happy path with mocked HTTP responses.
 */
class OpenAiHealthClientWithMockTest {
    
    /**
     * Test happy path: successful model listing using manual mock setup.
     */
    @Test
    void listModels_success_returnsModelNames() {
        // Create RestClient.Builder and bind MockRestServiceServer
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer mockServer = MockRestServiceServer.bindTo(builder).build();
        
        try {
            // Mock the /v1/models endpoint with realistic response
            String jsonResponse = """
                {
                    "object": "list",
                    "data": [
                        {
                            "id": "qwen3-coder6",
                            "object": "model",
                            "created": 1776343040,
                            "owned_by": "llamacpp"
                        },
                        {
                            "id": "gemma3:27b",
                            "object": "model",
                            "created": 1776343000,
                            "owned_by": "google"
                        }
                    ]
                }
                """;
            
            // Setup mock expectation - use full URL since we're not setting baseUrl
            mockServer.expect(requestTo("http://test-endpoint:8080/v1/models"))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));
            
            // Create client with mocked RestClient
            RestClient mockedClient = builder.baseUrl("http://test-endpoint:8080").build();
            OpenAiHealthClient client = new OpenAiHealthClient(mockedClient);
            
            assertNotNull(client);
            
            // Execute - this should trigger the mocked request
            List<String> modelNames = client.listModels().block();
            
            // Verify results
            assertNotNull(modelNames);
            assertEquals(2, modelNames.size());
            assertEquals("qwen3-coder6", modelNames.get(0));
            assertEquals("gemma3:27b", modelNames.get(1));
            
            // Verify the mock was called
            mockServer.verify();
        } finally {
            mockServer.reset();
        }
    }
    
    /**
     * Demonstrate MockRestServiceServer setup for future testing.
     */
    @Test
    void mockServerSetupWorks() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer mockServer = MockRestServiceServer.bindTo(builder).build();
        
        try {
            assertNotNull(mockServer);
            
            String jsonResponse = """
                {
                    "object": "list",
                    "data": [
                        {
                            "id": "test-model",
                            "object": "model",
                            "created": 1776343040,
                            "owned_by": "test"
                        }
                    ]
                }
                """;
            
            // Setup expectation
            mockServer.expect(requestTo("http://test:8080/v1/models"))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));
            
            // Create client and make actual request
            RestClient mockedClient = builder.baseUrl("http://test:8080").build();
            OpenAiHealthClient client = new OpenAiHealthClient(mockedClient);
            
            List<String> modelNames = client.listModels().block();
            
            assertNotNull(modelNames);
            assertEquals(1, modelNames.size());
            assertEquals("test-model", modelNames.get(0));
            
            mockServer.verify();
        } finally {
            mockServer.reset();
        }
    }
}

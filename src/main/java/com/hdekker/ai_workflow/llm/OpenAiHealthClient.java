package com.hdekker.ai_workflow.llm;

import java.util.List;
import java.util.stream.Collectors;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hdekker.ai_workflow.rest.dto.OpenAiModelsResponse;

import org.springframework.web.client.RestClient;
import reactor.core.publisher.Mono;

/**
 * REST client for OpenAI-compatible health checking.
 * Calls /v1/models endpoint to verify endpoint availability.
 */
public class OpenAiHealthClient {
    
    private static final Logger log = LoggerFactory.getLogger(OpenAiHealthClient.class);
    
    private final RestClient restClient;
    
    /**
     * Create OpenAiHealthClient with configured endpoint and timeout.
     * 
     * @param endpoint OpenAI-compatible endpoint URL (e.g., http://localhost:8080)
     * @param timeoutMs Timeout in milliseconds for health check calls
     */
    public OpenAiHealthClient(String endpoint, int timeoutMs) {
        this.restClient = RestClient.builder()
            .baseUrl(endpoint)
            .build();
    }
    
    /**
     * Create OpenAiHealthClient with pre-configured RestClient.
     * Used for testing with MockRestServiceServer.
     * 
     * @param restClient Pre-configured RestClient instance
     */
    public OpenAiHealthClient(RestClient restClient) {
        this.restClient = restClient;
    }
    
    /**
     * Create OpenAiHealthClient from a RestClient.Builder.
     * Used by @RestClientTest which binds MockRestServiceServer to the builder.
     * 
     * @param builder RestClient.Builder to build the client from
     */
    public OpenAiHealthClient(RestClient.Builder builder) {
        this.restClient = builder.build();
    }
    
    /**
     * List available models at the endpoint.
     * Returns model IDs as a list of strings.
     * 
     * @return Mono emitting list of model names, or error if endpoint unavailable
     */
    public Mono<List<String>> listModels() {
        return Mono.fromCallable(() -> {
            try {
                OpenAiModelsResponse response = restClient.get()
                    .uri("/v1/models")
                    .retrieve()
                    .body(OpenAiModelsResponse.class);
                
                if (response == null || response.data() == null) {
                    log.warn("Unexpected empty response from /v1/models");
                    return List.of();
                }
                
                List<String> modelNames = response.data().stream()
                    .map(OpenAiModelsResponse.OpenAiModel::id)
                    .filter(id -> id != null && !id.isEmpty())
                    .collect(Collectors.toList());
                
                log.debug("Retrieved {} models from endpoint", modelNames.size());
                return modelNames;
                
            } catch (Exception e) {
                log.error("Error calling /v1/models: {}", e.getMessage(), e);
                throw e;
            }
        });
    }
}

package com.hdekker.ai_workflow.adapter.outbound.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;


import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.restclient.test.autoconfigure.RestClientTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

@RestClientTest
class OpenAiHealthClientRestClientTest {

    @Configuration
    static class TestConfig {

        @Bean
        OpenAiHealthClient openAiHealthClient(RestClient.Builder builder) {
            return new OpenAiHealthClient(builder);
        }
    }

    @Autowired
    private OpenAiHealthClient client;

    @Autowired
    private MockRestServiceServer server;

    @Test
    void listModels_success_returnsModelNames() {
        String jsonResponse = """
            {
                "object": "list",
                "data": [
                    {"id": "gpt-4", "object": "model", "created": 1700000000, "owned_by": "openai"},
                    {"id": "gpt-3.5-turbo", "object": "model", "created": 1699000000, "owned_by": "openai"}
                ]
            }
            """;

        server.expect(requestTo("/v1/models"))
            .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        List<String> modelNames = client.listModels().block();

        assertThat(modelNames).hasSize(2);
        assertThat(modelNames).containsExactly("gpt-4", "gpt-3.5-turbo");
    }

    @Test
    void listModels_emptyResponse_returnsEmptyList() {
        String jsonResponse = """
            {
                "object": "list",
                "data": []
            }
            """;

        server.expect(requestTo("/v1/models"))
            .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        List<String> modelNames = client.listModels().block();

        assertThat(modelNames).isEmpty();
    }

    @Test
    void listModels_nullData_returnsEmptyList() {
        String jsonResponse = """
            {
                "object": "list",
                "data": null
            }
            """;

        server.expect(requestTo("/v1/models"))
            .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        List<String> modelNames = client.listModels().block();

        assertThat(modelNames).isEmpty();
    }

    @Test
    void listModels_nullAndEmptyIds_filteredOut() {
        String jsonResponse = """
            {
                "object": "list",
                "data": [
                    {"id": null, "object": "model", "created": 1700000000, "owned_by": "openai"},
                    {"id": "", "object": "model", "created": 1699000000, "owned_by": "openai"},
                    {"id": "valid-model", "object": "model", "created": 1698000000, "owned_by": "openai"}
                ]
            }
            """;

        server.expect(requestTo("/v1/models"))
            .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        List<String> modelNames = client.listModels().block();

        assertThat(modelNames).hasSize(1);
        assertThat(modelNames).containsExactly("valid-model");
    }

    @Test
    void listModels_serverError_propagatesException() {
        server.expect(requestTo("/v1/models"))
            .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withServerError());

        assertThrows(Exception.class, () -> client.listModels().block());
    }

    @Test
    void listModels_notFound_propagatesException() {
        server.expect(requestTo("/v1/models"))
            .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withStatus(HttpStatus.NOT_FOUND));

        assertThrows(Exception.class, () -> client.listModels().block());
    }

    @Test
    void listModels_malformedJson_propagatesException() {
        server.expect(requestTo("/v1/models"))
            .andRespond(withSuccess("not valid json", MediaType.APPLICATION_JSON));

        assertThrows(Exception.class, () -> client.listModels().block());
    }
}

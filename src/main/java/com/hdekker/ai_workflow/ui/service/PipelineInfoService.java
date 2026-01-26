package com.hdekker.ai_workflow.ui.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import reactor.core.publisher.Mono;

import com.hdekker.ai_workflow.rest.dto.PipelineInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class PipelineInfoService {

    private static final Logger log = LoggerFactory.getLogger(PipelineInfoService.class);

    private final WebClient webClient;

    @Autowired
    public PipelineInfoService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder
            .baseUrl("http://localhost:8080")
            .build();
    }

    public Mono<List<PipelineInfo>> getAllPipelineInfos() {
        return webClient.get()
            .uri("/api/pipelines")
            .retrieve()
            .bodyToFlux(PipelineInfo.class)
            .collectList()
            .doOnError(error -> log.error("Error fetching pipeline infos", error))
            .onErrorResume(WebClientResponseException.class, ex -> {
                log.error("HTTP error fetching pipeline infos: {} {}", ex.getStatusCode(), ex.getResponseBodyAsString());
                return Mono.just(List.of());
            })
            .onErrorResume(Exception.class, ex -> {
                log.error("Unexpected error fetching pipeline infos", ex);
                return Mono.just(List.of());
            });
    }

    public Mono<Void> deletePipeline(String id) {
        return webClient.delete()
            .uri("/api/pipelines/{id}", id)
            .retrieve()
            .bodyToMono(Void.class)
            .doOnError(error -> log.error("Error deleting pipeline with id: {}", id, error))
            .onErrorResume(WebClientResponseException.class, ex -> {
                log.error("HTTP error deleting pipeline {}: {} {}", id, ex.getStatusCode(), ex.getResponseBodyAsString());
                return Mono.empty();
            })
            .onErrorResume(Exception.class, ex -> {
                log.error("Unexpected error deleting pipeline with id: {}", id, ex);
                return Mono.empty();
            });
    }
}
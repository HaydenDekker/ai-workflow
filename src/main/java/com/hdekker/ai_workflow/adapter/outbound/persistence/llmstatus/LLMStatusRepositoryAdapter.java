package com.hdekker.ai_workflow.adapter.outbound.persistence.llmstatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.hdekker.ai_workflow.application.agent.port.LLMStatusRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Outbound adapter implementing {@link LLMStatusRepository} port.
 * <p>
 * Bridges the JPA-based {@link LLMStatusJpaRepository} to the application-layer
 * {@link LLMStatusRepository} port. Handles entity ↔ record mapping.
 */
@Service
public class LLMStatusRepositoryAdapter implements LLMStatusRepository {

    private final LLMStatusJpaRepository jpaRepository;

    public LLMStatusRepositoryAdapter(LLMStatusJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    @Transactional
    public void save(String endpoint, String configuredModel, String status,
                     LocalDateTime lastChecked, int modelCount, String modelNames,
                     String errorMessage) {
        LLMStatusEntity entity = jpaRepository.findByEndpoint(endpoint)
                .orElseGet(() -> new LLMStatusEntity());
        entity.setEndpoint(endpoint);
        entity.setConfiguredModel(configuredModel);
        entity.setStatus(status);
        entity.setLastChecked(lastChecked);
        entity.setModelCount(modelCount);
        entity.setModelNames(modelNames);
        entity.setErrorMessage(errorMessage);
        jpaRepository.save(entity);
    }

    @Override
    public Optional<LLMStatusRecord> findByEndpoint(String endpoint) {
        return jpaRepository.findByEndpoint(endpoint).map(this::toRecord);
    }

    @Override
    public List<LLMStatusRecord> findAll() {
        return jpaRepository.findAll().stream()
                .map(this::toRecord)
                .toList();
    }

    @Override
    @Transactional
    public void deleteByEndpoint(String endpoint) {
        jpaRepository.findByEndpoint(endpoint).ifPresent(jpaRepository::delete);
    }

    // ── Mapping ─────────────────────────────────────────────────────

    private LLMStatusRecord toRecord(LLMStatusEntity entity) {
        return new LLMStatusRecord(
                entity.getEndpoint(),
                entity.getConfiguredModel(),
                entity.getStatus(),
                entity.getLastChecked(),
                entity.getModelCount() != null ? entity.getModelCount() : 0,
                entity.getModelNames(),
                entity.getErrorMessage()
        );
    }
}

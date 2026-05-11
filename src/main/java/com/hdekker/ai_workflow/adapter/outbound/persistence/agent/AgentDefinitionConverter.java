package com.hdekker.ai_workflow.adapter.outbound.persistence.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hdekker.ai_workflow.domain.agent.AgentDefinition;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * JPA {@link AttributeConverter} for serializing/deserializing
 * {@link AgentDefinition} to/from JSON string for persistence.
 *
 * <p>Replaces manual Jackson calls in {@link AgentRepositoryAdapter}
 * with a declarative {@code @Convert} annotation on the entity field.</p>
 */
@Converter
public class AgentDefinitionConverter implements AttributeConverter<AgentDefinition, String> {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(AgentDefinition attribute) {
        if (attribute == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(attribute);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize AgentDefinition: " + e.getMessage(), e);
        }
    }

    @Override
    public AgentDefinition convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        try {
            return objectMapper.readValue(dbData, AgentDefinition.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to deserialize AgentDefinition: " + e.getMessage(), e);
        }
    }
}

package com.agentplatform.orchestrator.resume.persistence;

import com.agentplatform.orchestrator.resume.CareerTrackEvidence;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;

/**
 * JPA attribute converter that serializes a {@code List<CareerTrackEvidence>} to a JSON text column
 * and deserializes JSON text back to a Java list.
 *
 * <p>Compatible with PostgreSQL TEXT/VARCHAR and H2 for fast in-memory testing.</p>
 */
@Converter
public class CareerTrackEvidenceListJsonConverter implements AttributeConverter<List<CareerTrackEvidence>, String> {

    private static final Logger log = LoggerFactory.getLogger(CareerTrackEvidenceListJsonConverter.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<List<CareerTrackEvidence>> TYPE_REF = new TypeReference<>() {};

    @Override
    public String convertToDatabaseColumn(List<CareerTrackEvidence> attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return "[]";
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(attribute);
        } catch (Exception e) {
            log.error("Failed to serialize List<CareerTrackEvidence> to JSON: {}", e.getMessage());
            return "[]";
        }
    }

    @Override
    public List<CareerTrackEvidence> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank() || dbData.equals("[]")) {
            return Collections.emptyList();
        }
        try {
            return OBJECT_MAPPER.readValue(dbData, TYPE_REF);
        } catch (Exception e) {
            log.error("Failed to deserialize JSON to List<CareerTrackEvidence>: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
}

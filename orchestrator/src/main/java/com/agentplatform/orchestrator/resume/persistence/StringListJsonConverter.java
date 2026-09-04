package com.agentplatform.orchestrator.resume.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Converter
public class StringListJsonConverter
implements AttributeConverter<List<String>, String> {
    private static final Logger log = LoggerFactory.getLogger(StringListJsonConverter.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<List<String>> TYPE_REF = new TypeReference<List<String>>(){};

    public String convertToDatabaseColumn(List<String> attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return "[]";
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(attribute);
        }
        catch (Exception e) {
            log.error("Failed to serialize List<String> to JSON: {}", (Object)e.getMessage());
            return "[]";
        }
    }

    public List<String> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank() || dbData.equals("[]")) {
            return Collections.emptyList();
        }
        try {
            return (List)OBJECT_MAPPER.readValue(dbData, TYPE_REF);
        }
        catch (Exception e) {
            log.error("Failed to deserialize JSON to List<String>: {}", (Object)e.getMessage());
            return Collections.emptyList();
        }
    }
}


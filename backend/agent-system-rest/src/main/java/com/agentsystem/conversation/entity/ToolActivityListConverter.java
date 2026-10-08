package com.agentsystem.conversation.entity;

import com.agentsystem.schema.ToolActivity;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/** Stores a message's {@link ToolActivity} list as a JSON array; NULL when there were no tool calls. */
@Slf4j
@Converter
public class ToolActivityListConverter implements AttributeConverter<List<ToolActivity>, String> {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<List<ToolActivity>> TYPE = new TypeReference<>() {};

    @Override
    public String convertToDatabaseColumn(List<ToolActivity> activity) {
        if (activity == null || activity.isEmpty()) return null;
        try {
            return JSON.writeValueAsString(activity);
        } catch (Exception e) {
            log.warn("[ToolActivityListConverter] Could not serialize tool activity: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public List<ToolActivity> convertToEntityAttribute(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return JSON.readValue(json, TYPE);
        } catch (Exception e) {
            log.warn("[ToolActivityListConverter] Could not parse tool activity: {}", e.getMessage());
            return List.of();
        }
    }
}

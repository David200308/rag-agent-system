package com.agentsystem.conversation.entity;

import com.agentsystem.schema.ChoicePrompt;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.extern.slf4j.Slf4j;

/** Stores a message's {@link ChoicePrompt} as JSON; NULL when the assistant offered no options. */
@Slf4j
@Converter
public class ChoicePromptConverter implements AttributeConverter<ChoicePrompt, String> {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(ChoicePrompt prompt) {
        if (prompt == null) return null;
        try {
            return JSON.writeValueAsString(prompt);
        } catch (Exception e) {
            log.warn("[ChoicePromptConverter] Could not serialize choice prompt: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public ChoicePrompt convertToEntityAttribute(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return JSON.readValue(json, ChoicePrompt.class);
        } catch (Exception e) {
            log.warn("[ChoicePromptConverter] Could not parse choice prompt: {}", e.getMessage());
            return null;
        }
    }
}

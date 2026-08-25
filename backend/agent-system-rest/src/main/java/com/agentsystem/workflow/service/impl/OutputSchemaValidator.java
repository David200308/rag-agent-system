package com.agentsystem.workflow.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Validates JSON data against a practical subset of JSON Schema:
 * type (object/string/number/integer/boolean/array), properties, required, enum, items.
 * Not a full JSON Schema implementation — covers the shapes agents realistically emit.
 */
final class OutputSchemaValidator {

    private OutputSchemaValidator() {}

    /** Returns a list of human-readable error strings; empty means valid. */
    static List<String> validate(JsonNode schema, JsonNode data) {
        List<String> errors = new ArrayList<>();
        validateNode(schema, data, "$", errors);
        return errors;
    }

    private static void validateNode(JsonNode schema, JsonNode data, String path, List<String> errors) {
        if (schema == null || schema.isMissingNode()) return;

        JsonNode enumNode = schema.get("enum");
        if (enumNode != null && enumNode.isArray()) {
            boolean matched = false;
            for (JsonNode allowed : enumNode) {
                if (allowed.equals(data)) { matched = true; break; }
            }
            if (!matched) {
                errors.add(path + ": value not in allowed enum " + enumNode);
                return;
            }
        }

        JsonNode typeNode = schema.get("type");
        if (typeNode == null || !typeNode.isTextual()) return;
        String type = typeNode.asText();

        switch (type) {
            case "object" -> {
                if (!data.isObject()) {
                    errors.add(path + ": expected object, got " + kind(data));
                    return;
                }
                JsonNode required = schema.get("required");
                if (required != null && required.isArray()) {
                    for (JsonNode reqField : required) {
                        String field = reqField.asText();
                        if (!data.has(field)) {
                            errors.add(path + "." + field + ": required field missing");
                        }
                    }
                }
                JsonNode properties = schema.get("properties");
                if (properties != null && properties.isObject()) {
                    Iterator<Map.Entry<String, JsonNode>> fields = properties.fields();
                    while (fields.hasNext()) {
                        Map.Entry<String, JsonNode> entry = fields.next();
                        if (data.has(entry.getKey())) {
                            validateNode(entry.getValue(), data.get(entry.getKey()),
                                    path + "." + entry.getKey(), errors);
                        }
                    }
                }
            }
            case "array" -> {
                if (!data.isArray()) {
                    errors.add(path + ": expected array, got " + kind(data));
                    return;
                }
                JsonNode items = schema.get("items");
                if (items != null) {
                    for (int i = 0; i < data.size(); i++) {
                        validateNode(items, data.get(i), path + "[" + i + "]", errors);
                    }
                }
            }
            case "string" -> {
                if (!data.isTextual()) errors.add(path + ": expected string, got " + kind(data));
            }
            case "number" -> {
                if (!data.isNumber()) errors.add(path + ": expected number, got " + kind(data));
            }
            case "integer" -> {
                if (!data.isIntegralNumber()) errors.add(path + ": expected integer, got " + kind(data));
            }
            case "boolean" -> {
                if (!data.isBoolean()) errors.add(path + ": expected boolean, got " + kind(data));
            }
            default -> { /* unknown type keyword — skip, don't fail the whole schema */ }
        }
    }

    private static String kind(JsonNode node) {
        if (node == null || node.isMissingNode()) return "missing";
        if (node.isNull()) return "null";
        if (node.isObject()) return "object";
        if (node.isArray()) return "array";
        if (node.isTextual()) return "string";
        if (node.isBoolean()) return "boolean";
        if (node.isNumber()) return "number";
        return "unknown";
    }

    /** Best-effort JSON extraction: strips markdown code fences agents commonly wrap JSON in. */
    static JsonNode parseLenient(ObjectMapper mapper, String raw) throws Exception {
        String trimmed = raw.strip();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstNewline != -1 && lastFence > firstNewline) {
                trimmed = trimmed.substring(firstNewline + 1, lastFence).strip();
            }
        }
        return mapper.readTree(trimmed);
    }
}

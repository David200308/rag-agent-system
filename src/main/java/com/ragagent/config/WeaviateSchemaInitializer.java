package com.ragagent.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * Creates the Weaviate schema for the RagDocuments class on startup if it does not exist.
 *
 * Spring AI 1.1.0's WeaviateVectorStoreProperties does not support initialize-schema,
 * so schema creation is handled here instead.
 */
@Slf4j
@Component
public class WeaviateSchemaInitializer implements ApplicationRunner {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${spring.ai.vectorstore.weaviate.scheme:http}")
    private String scheme;

    @Value("${spring.ai.vectorstore.weaviate.host:localhost:8080}")
    private String host;

    @Value("${spring.ai.vectorstore.weaviate.object-class:SpringAiWeaviate}")
    private String objectClass;

    @Value("${spring.ai.vectorstore.weaviate.filter-field.source:TEXT}")
    private String sourceFieldType;

    @Value("${spring.ai.vectorstore.weaviate.filter-field.category:TEXT}")
    private String categoryFieldType;

    @Override
    public void run(ApplicationArguments args) {
        String baseUrl = scheme + "://" + host;
        String schemaUrl = baseUrl + "/v1/schema";

        try {
            // Check if class already exists
            ResponseEntity<Map> response = restTemplate.getForEntity(schemaUrl, Map.class);
            List<?> classes = (List<?>) response.getBody().get("classes");
            if (classes != null) {
                boolean exists = classes.stream()
                        .filter(c -> c instanceof Map)
                        .map(c -> (Map<?, ?>) c)
                        .anyMatch(c -> objectClass.equals(c.get("class")));
                if (exists) {
                    log.info("[WeaviateSchemaInitializer] Class '{}' already exists, skipping creation", objectClass);
                    return;
                }
            }

            // Create the class
            Map<String, Object> schema = Map.of(
                    "class", objectClass,
                    "vectorizer", "none",
                    "properties", List.of(
                            Map.of("name", "content",       "dataType", List.of("text"), "tokenization", "word"),
                            Map.of("name", "meta_source",   "dataType", List.of("text"), "tokenization", "word"),
                            Map.of("name", "meta_category", "dataType", List.of("text"), "tokenization", "word")
                    )
            );

            restTemplate.postForEntity(schemaUrl, schema, Map.class);
            log.info("[WeaviateSchemaInitializer] Created Weaviate class '{}'", objectClass);

        } catch (Exception e) {
            log.error("[WeaviateSchemaInitializer] Failed to initialize Weaviate schema: {}", e.getMessage());
        }
    }
}

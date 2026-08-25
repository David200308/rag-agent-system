package com.agentsystem.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the agent-system-storage-inner microservice integration.
 *
 * Bound from:
 *   storage.service-key  (env: STORAGE_SERVICE_KEY)
 *   storage.url          (env: STORAGE_URL)
 */
@ConfigurationProperties(prefix = "storage")
public record StorageProperties(String serviceKey, String url) {}

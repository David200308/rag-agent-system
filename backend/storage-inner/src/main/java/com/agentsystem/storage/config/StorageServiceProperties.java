package com.agentsystem.storage.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Shared secret this service expects on the {@code X-Storage-Key} header.
 *
 * Bound from:
 *   storage.service-key  (env: STORAGE_SERVICE_KEY)
 */
@ConfigurationProperties(prefix = "storage")
public record StorageServiceProperties(String serviceKey) {}

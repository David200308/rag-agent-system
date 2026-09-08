package com.agentsystem.travel.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Shared secret this service expects on the {@code X-Travel-Key} header.
 *
 * Bound from:
 *   travel.service-key  (env: TRAVEL_SERVICE_KEY)
 */
@ConfigurationProperties(prefix = "travel")
public record TravelServiceProperties(String serviceKey) {}

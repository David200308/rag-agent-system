package com.agentsystem.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the travel-inner microservice integration.
 *
 * Bound from:
 *   travel-inner.service-key  (env: TRAVEL_SERVICE_KEY)
 *   travel-inner.url          (env: TRAVEL_INNER_URL)
 */
@ConfigurationProperties(prefix = "travel-inner")
public record TravelInnerProperties(String serviceKey, String url) {}

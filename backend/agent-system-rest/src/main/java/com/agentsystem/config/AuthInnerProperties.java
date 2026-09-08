package com.agentsystem.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the auth-inner microservice integration.
 *
 * Bound from:
 *   auth-inner.service-key  (env: AUTH_SERVICE_KEY)
 *   auth-inner.url          (env: AUTH_INNER_URL)
 */
@ConfigurationProperties(prefix = "auth-inner")
public record AuthInnerProperties(String serviceKey, String url) {}

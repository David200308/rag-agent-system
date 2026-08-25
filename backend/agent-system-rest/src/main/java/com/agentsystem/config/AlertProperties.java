package com.agentsystem.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the Go investment-alert-task microservice integration.
 *
 * Bound from:
 *   alert.service-key  (env: ALERT_SERVICE_KEY)
 *   alert.url          (env: ALERT_URL)
 */
@ConfigurationProperties(prefix = "alert")
public record AlertProperties(String serviceKey, String url) {}

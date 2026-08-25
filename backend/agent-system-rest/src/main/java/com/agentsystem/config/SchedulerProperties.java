package com.agentsystem.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the Go scheduler microservice integration.
 *
 * Bound from:
 *   scheduler.service-key  (env: SCHEDULER_SERVICE_KEY)
 *   scheduler.url          (env: SCHEDULER_URL)
 */
@ConfigurationProperties(prefix = "scheduler")
public record SchedulerProperties(String serviceKey, String url) {}

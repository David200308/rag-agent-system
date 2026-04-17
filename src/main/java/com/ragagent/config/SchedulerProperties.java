package com.ragagent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the Go scheduler microservice integration.
 *
 * Bound from:
 *   scheduler.service-key  (env: SCHEDULER_SERVICE_KEY)
 */
@ConfigurationProperties(prefix = "scheduler")
public record SchedulerProperties(String serviceKey) {}

package com.agentsystem.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the agent-system-notification-inner microservice integration.
 *
 * Bound from:
 *   notification.service-key  (env: NOTIFICATION_SERVICE_KEY)
 *   notification.url          (env: NOTIFICATION_URL)
 */
@ConfigurationProperties(prefix = "notification")
public record NotificationProperties(String serviceKey, String url) {}

package com.agentsystem.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Shared secret this service expects on the {@code X-Notification-Key} header.
 *
 * Bound from:
 *   notification.service-key  (env: NOTIFICATION_SERVICE_KEY)
 */
@ConfigurationProperties(prefix = "notification")
public record NotificationServiceProperties(String serviceKey) {}

package com.agentsystem.financial.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Shared secret this service expects on the {@code X-Finance-Key} header.
 *
 * Bound from:
 *   finance.service-key  (env: FINANCE_SERVICE_KEY)
 */
@ConfigurationProperties(prefix = "finance")
public record FinanceServiceProperties(String serviceKey) {}

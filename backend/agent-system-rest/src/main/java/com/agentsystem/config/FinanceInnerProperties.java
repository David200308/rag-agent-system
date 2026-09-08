package com.agentsystem.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the finance-inner microservice integration.
 *
 * Bound from:
 *   finance-inner.service-key  (env: FINANCE_SERVICE_KEY)
 *   finance-inner.url          (env: FINANCE_INNER_URL)
 */
@ConfigurationProperties(prefix = "finance-inner")
public record FinanceInnerProperties(String serviceKey, String url) {}

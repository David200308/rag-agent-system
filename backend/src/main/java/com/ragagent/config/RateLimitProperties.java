package com.ragagent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "rate-limit")
public record RateLimitProperties(
        @DefaultValue("20")  long agentQueryLimit,
        @DefaultValue("10")  long ingestLimit,
        @DefaultValue("100") long defaultLimit
) {}

package com.ragagent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "fallback")
public record FallbackProperties(
        @DefaultValue("60") long cacheTtlMinutes
) {}

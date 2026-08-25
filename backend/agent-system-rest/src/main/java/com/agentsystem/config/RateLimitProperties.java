package com.agentsystem.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "rate-limit")
public record RateLimitProperties(
        @DefaultValue("20")  long agentQueryLimit,
        @DefaultValue("10")  long ingestLimit,
        @DefaultValue("100") long defaultLimit,
        @DefaultValue("5")   long otpLimit,
        @DefaultValue("5")   long registerOtpLimit,
        // Nothing in this stack sets X-Forwarded-For today (the Next.js frontend calls the
        // backend directly over the docker network without it), so trusting it unconditionally
        // lets any caller spoof a new value per request to bypass IP-based rate limiting.
        // Only enable this if you put a reverse proxy/load balancer in front that overwrites
        // X-Forwarded-For with the real client IP before it reaches this service.
        @DefaultValue("false") boolean trustForwardedFor
) {}

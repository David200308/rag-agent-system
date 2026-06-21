package com.agentsystem.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Auth/scoping for the MCP SSE transport (/mcp/**), which carries no per-request
 * JWT and is therefore exempted from AuthFilter/ClientIdentityFilter/RateLimitFilter.
 *
 * apiKey — shared secret every MCP client must send as "Authorization: Bearer <key>".
 *          Blank by default, which keeps /mcp/** closed to everyone (fail closed).
 * email  — the identity MCP tool calls act as. Used to scope search_knowledge to the
 *          same accessible-sources boundary RetrievalNode applies for normal chat.
 *          Blank by default, which restricts search_knowledge to no sources.
 */
@ConfigurationProperties(prefix = "mcp")
public record McpProperties(
        String apiKey,
        String email
) {}

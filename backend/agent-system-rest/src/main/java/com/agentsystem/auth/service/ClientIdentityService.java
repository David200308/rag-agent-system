package com.agentsystem.auth.service;

public interface ClientIdentityService {

    // ── iOS: HMAC-SHA256 ──────────────────────────────────────────────────────

    boolean verifyIos(String signature, String timestamp, String version,
                       String method, String path);

    // ── Web: static token (server-to-server) ─────────────────────────────────

    boolean verifyWebToken(String token);
}

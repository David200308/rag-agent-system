package com.agentsystem.auth.service;

import com.agentsystem.auth.ClientIdentityProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;

/**
 * Verifies client identity for iOS (HMAC-SHA256) and web (static token).
 *
 * iOS — HMAC-SHA256 signed per-request:
 *   Message: "ios:{version}:{METHOD}:{/api/path}:{unixTimestamp}"
 *   Header:  X-Mobile-Ios-Signature / X-Mobile-Ios-Timestamp / X-Mobile-Ios-Version
 *
 * Web — static pre-shared token (server-to-server, Next.js → Spring Boot):
 *   Header:  X-Web-Token: <CLIENT_WEB_SECRET>
 *   Check:   constant-time equality against configured secret
 *
 * CLI — Ed25519 per-user key, verified by CliKeyService.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClientIdentityService {

    private static final long   MAX_CLOCK_SKEW = 300;
    private static final String IOS_CLIENT     = "ios";

    private final ClientIdentityProperties props;

    // ── iOS: HMAC-SHA256 ──────────────────────────────────────────────────────

    public boolean verifyIos(String signature, String timestamp, String version,
                              String method, String path) {
        String secret = props.iosSecret();
        if (secret == null || secret.isBlank()) {
            log.warn("[ClientIdentity] No secret configured for iOS");
            return false;
        }

        long ts;
        try { ts = Long.parseLong(timestamp); }
        catch (NumberFormatException e) { return false; }

        long now = Instant.now().getEpochSecond();
        if (Math.abs(now - ts) > MAX_CLOCK_SKEW) {
            log.warn("[ClientIdentity] Stale iOS signature (skew={}s)", now - ts);
            return false;
        }

        String message = IOS_CLIENT + ":" + version + ":" + method.toUpperCase() + ":" + path + ":" + ts;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] expected = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            byte[] actual   = Base64.getDecoder().decode(signature);
            boolean valid   = MessageDigest.isEqual(expected, actual);
            if (!valid) log.warn("[ClientIdentity] Bad iOS signature");
            return valid;
        } catch (Exception e) {
            log.error("[ClientIdentity] iOS HMAC error: {}", e.getMessage());
            return false;
        }
    }

    // ── Web: static token (server-to-server) ─────────────────────────────────

    public boolean verifyWebToken(String token) {
        String configured = props.webSecret();
        if (configured == null || configured.isBlank()) {
            log.warn("[ClientIdentity] No web secret configured");
            return false;
        }
        // Constant-time comparison prevents timing attacks
        return MessageDigest.isEqual(
                configured.getBytes(StandardCharsets.UTF_8),
                token.getBytes(StandardCharsets.UTF_8));
    }
}

package com.ragagent.auth.service;

import com.ragagent.auth.AuthProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * Stateless JWT generation and validation.
 *
 * Tokens contain:
 *  - sub   : email address
 *  - mode  : PERSONAL | TEAM
 *  - orgId : org slug (TEAM mode only; absent for PERSONAL)
 *  - iat   : issued-at
 *  - exp   : expiry (auth.jwt-expiry-hours from config)
 */
@Slf4j
@Service
public class JwtService {

    public record TokenClaims(String email, String mode, String orgId) {}

    private final SecretKey key;
    private final long      expiryMillis;

    public JwtService(AuthProperties props) {
        byte[] keyBytes = props.jwtSecret().getBytes(StandardCharsets.UTF_8);
        byte[] padded = new byte[32];
        System.arraycopy(keyBytes, 0, padded, 0, Math.min(keyBytes.length, 32));
        this.key          = Keys.hmacShaKeyFor(padded);
        this.expiryMillis = (long) props.jwtExpiryHours() * 3_600_000L;
    }

    /** Create a signed JWT for personal mode. */
    public String generate(String email) {
        return generate(email, "PERSONAL", null);
    }

    /** Create a signed JWT with explicit mode and optional orgId. */
    public String generate(String email, String mode, String orgId) {
        long now = System.currentTimeMillis();
        var builder = Jwts.builder()
                .subject(email)
                .claim("mode", mode != null ? mode : "PERSONAL")
                .issuedAt(new Date(now))
                .expiration(new Date(now + expiryMillis))
                .signWith(key);
        if (orgId != null && !orgId.isBlank()) {
            builder.claim("orgId", orgId);
        }
        return builder.compact();
    }

    /**
     * Validate a token and return its claims, or {@code null} if invalid/expired.
     */
    public TokenClaims validateFull(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            String mode  = claims.get("mode", String.class);
            String orgId = claims.get("orgId", String.class);
            return new TokenClaims(claims.getSubject(), mode != null ? mode : "PERSONAL", orgId);
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("[JwtService] Invalid token: {}", e.getMessage());
            return null;
        }
    }

    /** Backward-compatible: returns email only (used by passkey path and register-key). */
    public String validate(String token) {
        TokenClaims c = validateFull(token);
        return c != null ? c.email() : null;
    }
}

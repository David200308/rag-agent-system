package com.agentsystem.auth.service.impl;

import com.agentsystem.auth.service.JwtService;

import com.agentsystem.auth.AuthProperties;
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
 *  - sub   : user_uuid (not the numeric users.id, and never the raw email)
 *  - mode  : PERSONAL | TEAM
 *  - orgId : org slug (TEAM mode only; absent for PERSONAL)
 *  - iat   : issued-at
 *  - exp   : expiry (auth.jwt-expiry-hours from config)
 */
@Slf4j
@Service
public class JwtServiceImpl implements JwtService {

    private final SecretKey key;
    private final long      expiryMillis;

    /**
     * @throws io.jsonwebtoken.security.WeakKeyException if auth.jwt-secret is under 256 bits
     *         (32 bytes) — fails app startup rather than silently zero-padding a short secret
     *         into a low-entropy key.
     */
    public JwtServiceImpl(AuthProperties props) {
        byte[] keyBytes = props.jwtSecret().getBytes(StandardCharsets.UTF_8);
        this.key          = Keys.hmacShaKeyFor(keyBytes);
        this.expiryMillis = (long) props.jwtExpiryHours() * 3_600_000L;
    }

    /** Create a signed JWT for personal mode. */
    @Override
    public String generate(String userUuid) {
        return generate(userUuid, "PERSONAL", null);
    }

    /** Create a signed JWT with explicit mode and optional orgId. */
    @Override
    public String generate(String userUuid, String mode, String orgId) {
        long now = System.currentTimeMillis();
        var builder = Jwts.builder()
                .subject(userUuid)
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
    @Override
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

    /** Backward-compatible: returns the user_uuid subject only. */
    @Override
    public String validate(String token) {
        TokenClaims c = validateFull(token);
        return c != null ? c.userUuid() : null;
    }
}

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
 *  - sub  : email address
 *  - iat  : issued-at
 *  - exp  : expiry (auth.jwt-expiry-hours from config)
 */
@Slf4j
@Service
public class JwtService {

    private final SecretKey key;
    private final long      expiryMillis;

    public JwtService(AuthProperties props) {
        // Derive a ≥256-bit HMAC-SHA256 key from the configured secret
        byte[] keyBytes = props.jwtSecret().getBytes(StandardCharsets.UTF_8);
        // Pad/truncate to exactly 32 bytes so JJWT is happy
        byte[] padded = new byte[32];
        System.arraycopy(keyBytes, 0, padded, 0, Math.min(keyBytes.length, 32));
        this.key          = Keys.hmacShaKeyFor(padded);
        this.expiryMillis = (long) props.jwtExpiryHours() * 3_600_000L;
    }

    /** Create a signed JWT for the given email. */
    public String generate(String email) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(email)
                .issuedAt(new Date(now))
                .expiration(new Date(now + expiryMillis))
                .signWith(key)
                .compact();
    }

    /**
     * Validate a token and return the email subject, or {@code null} if invalid/expired.
     */
    public String validate(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return claims.getSubject();
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("[JwtService] Invalid token: {}", e.getMessage());
            return null;
        }
    }
}

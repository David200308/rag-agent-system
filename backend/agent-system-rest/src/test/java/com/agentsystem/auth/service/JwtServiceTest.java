package com.agentsystem.auth.service;

import com.agentsystem.auth.service.impl.JwtServiceImpl;

import com.agentsystem.auth.AuthProperties;
import io.jsonwebtoken.security.WeakKeyException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    // ── constructor — secret strength ────────────────────────────────────────

    @Test
    void constructor_secretUnder32Bytes_throwsWeakKeyException() {
        AuthProperties shortSecretProps = new AuthProperties(true, 10, "too-short-secret", 24);

        assertThatThrownBy(() -> new JwtServiceImpl(shortSecretProps))
                .isInstanceOf(WeakKeyException.class);
    }

    @Test
    void constructor_secretExactly32Bytes_doesNotThrow() {
        AuthProperties props32 = new AuthProperties(true, 10, "a".repeat(32), 24);

        JwtService service = new JwtServiceImpl(props32);

        assertThat(service.generate("user@example.com")).isNotBlank();
    }

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        AuthProperties props = new AuthProperties(
                true,
                10,
                "test-secret-key-that-is-at-least-32-characters-long",
                24
        );
        jwtService = new JwtServiceImpl(props);
    }

    @Test
    void generate_producesNonBlankToken() {
        String token = jwtService.generate("user@example.com");
        assertThat(token).isNotBlank();
    }

    @Test
    void validate_validToken_returnsEmailSubject() {
        String token = jwtService.generate("user@example.com");
        assertThat(jwtService.validate(token)).isEqualTo("user@example.com");
    }

    @Test
    void validate_tamperedToken_returnsNull() {
        String token = jwtService.generate("user@example.com");
        String tampered = token.substring(0, token.length() - 5) + "XXXXX";
        assertThat(jwtService.validate(tampered)).isNull();
    }

    @Test
    void validate_randomString_returnsNull() {
        assertThat(jwtService.validate("not.a.jwt")).isNull();
    }

    @Test
    void validate_blankString_returnsNull() {
        assertThat(jwtService.validate("")).isNull();
    }

    @Test
    void generate_differentEmails_produceDifferentTokens() {
        String t1 = jwtService.generate("alice@example.com");
        String t2 = jwtService.generate("bob@example.com");
        assertThat(t1).isNotEqualTo(t2);
    }

    // ── generate with mode + orgId ────────────────────────────────────────────

    @Test
    void generate_personalMode_tokenContainsPersonalClaims() {
        String token = jwtService.generate("user@example.com", "PERSONAL", null);
        JwtService.TokenClaims claims = jwtService.validateFull(token);
        assertThat(claims).isNotNull();
        assertThat(claims.userUuid()).isEqualTo("user@example.com");
        assertThat(claims.mode()).isEqualTo("PERSONAL");
        assertThat(claims.orgId()).isNull();
    }

    @Test
    void generate_teamMode_tokenContainsOrgId() {
        String token = jwtService.generate("user@example.com", "TEAM", "skyproton");
        JwtService.TokenClaims claims = jwtService.validateFull(token);
        assertThat(claims).isNotNull();
        assertThat(claims.userUuid()).isEqualTo("user@example.com");
        assertThat(claims.mode()).isEqualTo("TEAM");
        assertThat(claims.orgId()).isEqualTo("skyproton");
    }

    @Test
    void generate_noArg_defaultsToPersonalMode() {
        String token = jwtService.generate("user@example.com");
        JwtService.TokenClaims claims = jwtService.validateFull(token);
        assertThat(claims).isNotNull();
        assertThat(claims.mode()).isEqualTo("PERSONAL");
        assertThat(claims.orgId()).isNull();
    }

    // ── validateFull ──────────────────────────────────────────────────────────

    @Test
    void validateFull_invalidToken_returnsNull() {
        assertThat(jwtService.validateFull("not.a.valid.token")).isNull();
    }

    @Test
    void validateFull_tamperedToken_returnsNull() {
        String token = jwtService.generate("user@example.com", "TEAM", "acme");
        String tampered = token.substring(0, token.length() - 5) + "XXXXX";
        assertThat(jwtService.validateFull(tampered)).isNull();
    }

    @Test
    void validate_expiredToken_returnsNull() {
        // Token signed with 0-hour expiry should be instantly expired
        AuthProperties shortProps = new AuthProperties(true, 10,
                "test-secret-key-that-is-at-least-32-characters-long", 0);
        JwtService shortLived = new JwtServiceImpl(shortProps);
        String token = shortLived.generate("user@example.com");
        // Token expires immediately (0 hours = 0 ms), so validation should return null
        assertThat(shortLived.validate(token)).isNull();
    }
}

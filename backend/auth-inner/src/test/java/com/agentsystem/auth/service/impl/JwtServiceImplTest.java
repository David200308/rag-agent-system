package com.agentsystem.auth.service.impl;

import com.agentsystem.auth.AuthProperties;
import com.agentsystem.auth.service.JwtService;
import io.jsonwebtoken.security.WeakKeyException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceImplTest {

    // ── constructor — secret strength ────────────────────────────────────────

    @Test
    void constructor_secretUnder32Bytes_throwsWeakKeyException() {
        AuthProperties shortSecretProps = new AuthProperties("too-short-secret", 24, 10, "test-service-key");

        assertThatThrownBy(() -> new JwtServiceImpl(shortSecretProps))
                .isInstanceOf(WeakKeyException.class);
    }

    @Test
    void constructor_secretExactly32Bytes_doesNotThrow() {
        AuthProperties props32 = new AuthProperties("a".repeat(32), 24, 10, "test-service-key");

        JwtService service = new JwtServiceImpl(props32);

        assertThat(service.generate("user-uuid-1", "PERSONAL", null)).isNotBlank();
    }

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        AuthProperties props = new AuthProperties(
                "test-secret-key-that-is-at-least-32-characters-long",
                24, 10, "test-service-key"
        );
        jwtService = new JwtServiceImpl(props);
    }

    @Test
    void generate_producesNonBlankToken() {
        String token = jwtService.generate("user-uuid-1", "PERSONAL", null);
        assertThat(token).isNotBlank();
    }

    @Test
    void generate_differentUuids_produceDifferentTokens() {
        String t1 = jwtService.generate("uuid-alice", "PERSONAL", null);
        String t2 = jwtService.generate("uuid-bob", "PERSONAL", null);
        assertThat(t1).isNotEqualTo(t2);
    }

    // ── generate with mode + orgId ────────────────────────────────────────────

    @Test
    void generate_personalMode_tokenContainsPersonalClaims() {
        String token = jwtService.generate("user-uuid-1", "PERSONAL", null);
        JwtService.TokenClaims claims = jwtService.validateFull(token);
        assertThat(claims).isNotNull();
        assertThat(claims.userUuid()).isEqualTo("user-uuid-1");
        assertThat(claims.mode()).isEqualTo("PERSONAL");
        assertThat(claims.orgId()).isNull();
    }

    @Test
    void generate_teamMode_tokenContainsOrgId() {
        String token = jwtService.generate("user-uuid-1", "TEAM", "skyproton");
        JwtService.TokenClaims claims = jwtService.validateFull(token);
        assertThat(claims).isNotNull();
        assertThat(claims.userUuid()).isEqualTo("user-uuid-1");
        assertThat(claims.mode()).isEqualTo("TEAM");
        assertThat(claims.orgId()).isEqualTo("skyproton");
    }

    @Test
    void generate_nullMode_defaultsToPersonal() {
        String token = jwtService.generate("user-uuid-1", null, null);
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
        String token = jwtService.generate("user-uuid-1", "TEAM", "acme");
        String tampered = token.substring(0, token.length() - 5) + "XXXXX";
        assertThat(jwtService.validateFull(tampered)).isNull();
    }

    @Test
    void validateFull_randomString_returnsNull() {
        assertThat(jwtService.validateFull("not.a.jwt")).isNull();
    }

    @Test
    void validateFull_blankString_returnsNull() {
        assertThat(jwtService.validateFull("")).isNull();
    }

    @Test
    void validateFull_expiredToken_returnsNull() {
        // Token signed with 0-hour expiry should be instantly expired
        AuthProperties shortProps = new AuthProperties(
                "test-secret-key-that-is-at-least-32-characters-long", 0, 10, "test-service-key");
        JwtService shortLived = new JwtServiceImpl(shortProps);
        String token = shortLived.generate("user-uuid-1", "PERSONAL", null);
        assertThat(shortLived.validateFull(token)).isNull();
    }
}

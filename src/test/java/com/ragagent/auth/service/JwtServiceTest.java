package com.ragagent.auth.service;

import com.ragagent.auth.AuthProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        AuthProperties props = new AuthProperties(
                true,
                10,
                "test-secret-key-that-is-at-least-32-characters-long",
                24,
                null
        );
        jwtService = new JwtService(props);
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

    @Test
    void validate_expiredToken_returnsNull() {
        // Token signed with 0-hour expiry should be instantly expired
        AuthProperties shortProps = new AuthProperties(true, 10,
                "test-secret-key-that-is-at-least-32-characters-long", 0, null);
        JwtService shortLived = new JwtService(shortProps);
        String token = shortLived.generate("user@example.com");
        // Token expires immediately (0 hours = 0 ms), so validation should return null
        assertThat(shortLived.validate(token)).isNull();
    }
}

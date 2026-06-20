package com.ragagent.auth.service;

import com.ragagent.auth.AuthProperties;
import com.ragagent.auth.entity.EmailWhitelist;
import com.ragagent.auth.repository.EmailWhitelistRepository;
import com.ragagent.org.OrganizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock EmailWhitelistRepository        whitelistRepo;
    @Mock StringRedisTemplate             redisTemplate;
    @Mock ValueOperations<String, String> valueOperations;
    @Mock EmailService                    emailService;
    @Mock JwtService                      jwtService;
    @Mock OrganizationService             orgService;

    // AuthProperties is a record (final) — instantiate directly
    private final AuthProperties authProperties =
            new AuthProperties(true, 10, "test-jwt-secret-32-chars-xxxxxxxxx", 24);

    AuthService authService;

    /** otp:<email> → code, backing the mocked StringRedisTemplate below. */
    Map<String, String> otpBacking;

    @BeforeEach
    void setUp() {
        otpBacking = new ConcurrentHashMap<>();
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().doAnswer(inv -> otpBacking.put(inv.getArgument(0), inv.getArgument(1)))
                .when(valueOperations).set(anyString(), anyString(), any(Duration.class));
        lenient().when(valueOperations.get(anyString()))
                .thenAnswer(inv -> otpBacking.get(inv.getArgument(0)));
        lenient().when(redisTemplate.delete(anyString()))
                .thenAnswer(inv -> otpBacking.remove((String) inv.getArgument(0)) != null);

        authService = new AuthService(authProperties, whitelistRepo, redisTemplate, emailService, jwtService, orgService);
    }

    // ── requestOtp ────────────────────────────────────────────────────────────

    @Test
    void requestOtp_whitelistedEmail_savesOtpAndSendsEmail() {
        when(whitelistRepo.findByEmailIgnoreCaseAndEnabledTrue("user@example.com"))
                .thenReturn(Optional.of(new EmailWhitelist("user@example.com")));

        authService.requestOtp("User@Example.COM");

        verify(valueOperations).set(eq("otp:user@example.com"), anyString(), eq(Duration.ofMinutes(10)));
        verify(emailService).sendOtp(eq("user@example.com"), anyString(), eq(10));
    }

    @Test
    void requestOtp_notWhitelisted_throwsIllegalArgument() {
        when(whitelistRepo.findByEmailIgnoreCaseAndEnabledTrue("unknown@example.com"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.requestOtp("unknown@example.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not authorised");
    }

    @Test
    void requestOtp_normalisesEmailToLowercase() {
        when(whitelistRepo.findByEmailIgnoreCaseAndEnabledTrue("admin@example.com"))
                .thenReturn(Optional.of(new EmailWhitelist("admin@example.com")));

        authService.requestOtp("  ADMIN@Example.com  ");

        ArgumentCaptor<String> emailCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendOtp(emailCaptor.capture(), anyString(), anyInt());
        assertThat(emailCaptor.getValue()).isEqualTo("admin@example.com");
    }

    // ── verifyOtp ─────────────────────────────────────────────────────────────

    @Test
    void verifyOtp_validCode_returnsJwt() {
        otpBacking.put("otp:user@example.com", "123456");
        when(jwtService.generate("user@example.com", "PERSONAL", null)).thenReturn("signed-jwt");

        String token = authService.verifyOtp("user@example.com", "123456");

        assertThat(token).isEqualTo("signed-jwt");
        assertThat(otpBacking).doesNotContainKey("otp:user@example.com");
    }

    @Test
    void verifyOtp_teamMode_validMember_returnsJwt() {
        otpBacking.put("otp:user@example.com", "123456");
        when(orgService.isMember("skyproton", "user@example.com")).thenReturn(true);
        when(jwtService.generate("user@example.com", "TEAM", "skyproton")).thenReturn("team-jwt");

        String token = authService.verifyOtp("user@example.com", "123456", "TEAM", "skyproton");

        assertThat(token).isEqualTo("team-jwt");
        verify(orgService).isMember("skyproton", "user@example.com");
    }

    @Test
    void verifyOtp_teamMode_notMember_throwsIllegalArgument() {
        otpBacking.put("otp:user@example.com", "123456");
        when(orgService.isMember("unknown-org", "user@example.com")).thenReturn(false);

        assertThatThrownBy(() ->
                authService.verifyOtp("user@example.com", "123456", "TEAM", "unknown-org"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not a member");
    }

    @Test
    void verifyOtp_teamMode_missingOrgId_throwsIllegalArgument() {
        otpBacking.put("otp:user@example.com", "123456");

        assertThatThrownBy(() ->
                authService.verifyOtp("user@example.com", "123456", "TEAM", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("orgId is required");
    }

    @Test
    void verifyOtp_wrongCode_throwsIllegalArgument() {
        otpBacking.put("otp:user@example.com", "123456");

        assertThatThrownBy(() -> authService.verifyOtp("user@example.com", "999999"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid or expired");
    }

    @Test
    void verifyOtp_noValidOtp_throwsIllegalArgument() {
        assertThatThrownBy(() -> authService.verifyOtp("user@example.com", "123456"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid or expired");
    }

    // ── validateToken ─────────────────────────────────────────────────────────

    @Test
    void validateToken_delegatesToJwtService() {
        when(jwtService.validate("my-token")).thenReturn("user@example.com");

        assertThat(authService.validateToken("my-token")).isEqualTo("user@example.com");
    }

    @Test
    void validateToken_invalidToken_returnsNull() {
        when(jwtService.validate("bad-token")).thenReturn(null);

        assertThat(authService.validateToken("bad-token")).isNull();
    }

    @Test
    void validateTokenFull_delegatesToJwtService() {
        JwtService.TokenClaims claims = new JwtService.TokenClaims("user@example.com", "TEAM", "acme");
        when(jwtService.validateFull("my-token")).thenReturn(claims);

        assertThat(authService.validateTokenFull("my-token")).isSameAs(claims);
    }
}

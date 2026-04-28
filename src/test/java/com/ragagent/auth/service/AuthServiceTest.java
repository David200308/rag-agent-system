package com.ragagent.auth.service;

import com.ragagent.auth.AuthProperties;
import com.ragagent.auth.entity.EmailWhitelist;
import com.ragagent.auth.entity.OtpCode;
import com.ragagent.auth.repository.EmailWhitelistRepository;
import com.ragagent.auth.repository.OtpCodeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock EmailWhitelistRepository whitelistRepo;
    @Mock OtpCodeRepository        otpRepo;
    @Mock EmailService             emailService;
    @Mock JwtService               jwtService;

    // AuthProperties is a record (final) — instantiate directly
    private final AuthProperties authProperties =
            new AuthProperties(true, 10, "test-jwt-secret-32-chars-xxxxxxxxx", 24, null);

    AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(authProperties, whitelistRepo, otpRepo, emailService, jwtService);
    }

    // ── requestOtp ────────────────────────────────────────────────────────────

    @Test
    void requestOtp_whitelistedEmail_savesOtpAndSendsEmail() {
        when(whitelistRepo.findByEmailIgnoreCaseAndEnabledTrue("user@example.com"))
                .thenReturn(Optional.of(new EmailWhitelist("user@example.com")));

        authService.requestOtp("User@Example.COM");

        verify(otpRepo).deleteAllByEmail("user@example.com");
        verify(otpRepo).save(any(OtpCode.class));
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
        OtpCode otp = new OtpCode("user@example.com", "123456",
                LocalDateTime.now().plusMinutes(5));
        when(otpRepo.findValidOtp(eq("user@example.com"), any(LocalDateTime.class)))
                .thenReturn(Optional.of(otp));
        when(jwtService.generate("user@example.com")).thenReturn("signed-jwt");

        String token = authService.verifyOtp("user@example.com", "123456");

        assertThat(token).isEqualTo("signed-jwt");
        assertThat(otp.isUsed()).isTrue();
        verify(otpRepo).save(otp);
        verify(otpRepo).deleteAllByEmail("user@example.com");
    }

    @Test
    void verifyOtp_wrongCode_throwsIllegalArgument() {
        OtpCode otp = new OtpCode("user@example.com", "123456",
                LocalDateTime.now().plusMinutes(5));
        when(otpRepo.findValidOtp(eq("user@example.com"), any(LocalDateTime.class)))
                .thenReturn(Optional.of(otp));

        assertThatThrownBy(() -> authService.verifyOtp("user@example.com", "999999"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid or expired");
    }

    @Test
    void verifyOtp_noValidOtp_throwsIllegalArgument() {
        when(otpRepo.findValidOtp(anyString(), any(LocalDateTime.class)))
                .thenReturn(Optional.empty());

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

    // ── cleanupExpired ────────────────────────────────────────────────────────

    @Test
    void cleanupExpired_deletesExpiredOtps() {
        authService.cleanupExpired();

        verify(otpRepo).deleteExpired(any(LocalDateTime.class));
    }
}

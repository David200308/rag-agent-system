package com.agentsystem.auth.service.impl;

import com.agentsystem.auth.AuthProperties;
import com.agentsystem.auth.notification.NotificationClient;
import com.agentsystem.auth.org.OrgLookupService;
import com.agentsystem.auth.service.AuthService;
import com.agentsystem.auth.service.JwtService;
import com.agentsystem.auth.user.AuthUser;
import com.agentsystem.auth.user.AuthUserService;
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
class AuthServiceImplTest {

    @Mock AuthUserService                 userAccountService;
    @Mock StringRedisTemplate             redisTemplate;
    @Mock ValueOperations<String, String> valueOperations;
    @Mock NotificationClient              notificationClient;
    @Mock JwtService                      jwtService;
    @Mock OrgLookupService                orgLookupService;

    // AuthProperties is a record (final) — instantiate directly.
    // Field order in this module: (jwtSecret, jwtExpiryHours, otpExpiryMinutes, serviceKey).
    private final AuthProperties authProperties =
            new AuthProperties("test-jwt-secret-32-chars-xxxxxxxxx", 24, 10, "test-service-key");

    AuthService authService;

    /** otp:<email> / register-otp:<email> → code, backing the mocked StringRedisTemplate below. */
    Map<String, String> otpBacking;
    /** otp:attempts:<email> / register-otp:attempts:<email> → failed-attempt count. */
    Map<String, Long> attemptsBacking;

    private static AuthUser activeUser(String email) {
        return new AuthUser("uuid-" + email, email, "USER", true);
    }

    @BeforeEach
    void setUp() {
        otpBacking      = new ConcurrentHashMap<>();
        attemptsBacking = new ConcurrentHashMap<>();
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().doAnswer(inv -> otpBacking.put(inv.getArgument(0), inv.getArgument(1)))
                .when(valueOperations).set(anyString(), anyString(), any(Duration.class));
        lenient().when(valueOperations.get(anyString()))
                .thenAnswer(inv -> otpBacking.get(inv.getArgument(0)));
        lenient().when(valueOperations.increment(anyString()))
                .thenAnswer(inv -> attemptsBacking.merge(inv.getArgument(0), 1L, Long::sum));
        lenient().when(redisTemplate.delete(anyString()))
                .thenAnswer(inv -> {
                    String k = inv.getArgument(0);
                    boolean removedOtp      = otpBacking.remove(k) != null;
                    boolean removedAttempts = attemptsBacking.remove(k) != null;
                    return removedOtp || removedAttempts;
                });

        authService = new AuthServiceImpl(authProperties, userAccountService, redisTemplate, notificationClient, jwtService, orgLookupService);
    }

    // ── requestOtp ────────────────────────────────────────────────────────────

    @Test
    void requestOtp_activeUser_savesOtpAndSendsEmail() {
        when(userAccountService.findActiveUser("user@example.com"))
                .thenReturn(Optional.of(activeUser("user@example.com")));

        authService.requestOtp("User@Example.COM");

        verify(valueOperations).set(eq("otp:user@example.com"), anyString(), eq(Duration.ofMinutes(10)));
        verify(notificationClient).sendOtp(eq("user@example.com"), anyString(), eq(10));
    }

    @Test
    void requestOtp_noSuchUser_throwsIllegalArgument() {
        when(userAccountService.findActiveUser("unknown@example.com")).thenReturn(Optional.empty());
        when(userAccountService.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.requestOtp("unknown@example.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("register first");
    }

    @Test
    void requestOtp_pendingUser_throwsPendingApprovalMessage() {
        when(userAccountService.findActiveUser("pending@example.com")).thenReturn(Optional.empty());
        when(userAccountService.findByEmail("pending@example.com"))
                .thenReturn(Optional.of(new AuthUser("uuid-1", "pending@example.com", "PRE_USER", true)));

        assertThatThrownBy(() -> authService.requestOtp("pending@example.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pending approval");
    }

    @Test
    void requestOtp_normalisesEmailToLowercase() {
        when(userAccountService.findActiveUser("admin@example.com"))
                .thenReturn(Optional.of(activeUser("admin@example.com")));

        authService.requestOtp("  ADMIN@Example.com  ");

        ArgumentCaptor<String> emailCaptor = ArgumentCaptor.forClass(String.class);
        verify(notificationClient).sendOtp(emailCaptor.capture(), anyString(), anyInt());
        assertThat(emailCaptor.getValue()).isEqualTo("admin@example.com");
    }

    // ── verifyOtp ─────────────────────────────────────────────────────────────

    @Test
    void verifyOtp_validCode_returnsJwt() {
        otpBacking.put("otp:user@example.com", "123456");
        AuthUser user = activeUser("user@example.com");
        when(userAccountService.findActiveUser("user@example.com")).thenReturn(Optional.of(user));
        when(jwtService.generate(user.getUuid(), "PERSONAL", null)).thenReturn("signed-jwt");

        String token = authService.verifyOtp("user@example.com", "123456", "PERSONAL", null);

        assertThat(token).isEqualTo("signed-jwt");
        assertThat(otpBacking).doesNotContainKey("otp:user@example.com");
    }

    @Test
    void verifyOtp_teamMode_validMember_returnsJwt() {
        otpBacking.put("otp:user@example.com", "123456");
        AuthUser user = activeUser("user@example.com");
        when(orgLookupService.isMember("skyproton", "user@example.com")).thenReturn(true);
        when(userAccountService.findActiveUser("user@example.com")).thenReturn(Optional.of(user));
        when(jwtService.generate(user.getUuid(), "TEAM", "skyproton")).thenReturn("team-jwt");

        String token = authService.verifyOtp("user@example.com", "123456", "TEAM", "skyproton");

        assertThat(token).isEqualTo("team-jwt");
        verify(orgLookupService).isMember("skyproton", "user@example.com");
    }

    @Test
    void verifyOtp_teamMode_notMember_throwsIllegalArgument() {
        otpBacking.put("otp:user@example.com", "123456");
        when(orgLookupService.isMember("unknown-org", "user@example.com")).thenReturn(false);

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

        assertThatThrownBy(() -> authService.verifyOtp("user@example.com", "999999", "PERSONAL", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid or expired");
    }

    @Test
    void verifyOtp_noValidOtp_throwsIllegalArgument() {
        assertThatThrownBy(() -> authService.verifyOtp("user@example.com", "123456", "PERSONAL", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid or expired");
    }

    @Test
    void verifyOtp_noLongerActive_throwsIllegalArgument() {
        otpBacking.put("otp:user@example.com", "123456");
        when(userAccountService.findActiveUser("user@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.verifyOtp("user@example.com", "123456", "PERSONAL", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no longer active");
    }

    @Test
    void verifyOtp_successResetsAttemptCounter() {
        otpBacking.put("otp:user@example.com", "123456");
        AuthUser user = activeUser("user@example.com");
        when(userAccountService.findActiveUser("user@example.com")).thenReturn(Optional.of(user));
        when(jwtService.generate(user.getUuid(), "PERSONAL", null)).thenReturn("signed-jwt");

        authService.verifyOtp("user@example.com", "123456", "PERSONAL", null);

        assertThat(attemptsBacking).doesNotContainKey("otp:attempts:user@example.com");
    }

    @Test
    void verifyOtp_tooManyWrongAttempts_locksOutEvenWithCorrectCode() {
        otpBacking.put("otp:user@example.com", "123456");

        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> authService.verifyOtp("user@example.com", "999999", "PERSONAL", null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid or expired");
        }

        // 6th attempt is locked out regardless of whether the code is now correct.
        assertThatThrownBy(() -> authService.verifyOtp("user@example.com", "123456", "PERSONAL", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Too many incorrect attempts");
        verify(jwtService, never()).generate(any(), any(), any());
    }

    @Test
    void requestOtp_resetsExistingLockout() {
        attemptsBacking.put("otp:attempts:user@example.com", 5L);
        when(userAccountService.findActiveUser("user@example.com"))
                .thenReturn(Optional.of(activeUser("user@example.com")));

        authService.requestOtp("user@example.com");

        assertThat(attemptsBacking).doesNotContainKey("otp:attempts:user@example.com");
    }

    // ── registration OTP ──────────────────────────────────────────────────────

    @Test
    void requestRegistrationOtp_anyEmail_sendsCodeUnderDistinctPrefix() {
        authService.requestRegistrationOtp("new@example.com");

        verify(valueOperations).set(eq("register-otp:new@example.com"), anyString(), eq(Duration.ofMinutes(10)));
        verify(notificationClient).sendOtp(eq("new@example.com"), anyString(), eq(10));
        verifyNoInteractions(userAccountService);
    }

    @Test
    void verifyRegistrationOtp_validCode_registersPendingUser() {
        otpBacking.put("register-otp:new@example.com", "123456");
        AuthUser pending = new AuthUser("uuid-2", "new@example.com", "PRE_USER", true);
        when(userAccountService.registerOrGetPending("new@example.com")).thenReturn(pending);

        String status = authService.verifyRegistrationOtp("new@example.com", "123456");

        assertThat(status).isEqualTo("PRE_USER");
        assertThat(otpBacking).doesNotContainKey("register-otp:new@example.com");
    }

    @Test
    void verifyRegistrationOtp_wrongCode_throwsIllegalArgumentAndNeverRegisters() {
        otpBacking.put("register-otp:new@example.com", "123456");

        assertThatThrownBy(() -> authService.verifyRegistrationOtp("new@example.com", "000000"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid or expired");
        verify(userAccountService, never()).registerOrGetPending(anyString());
    }

    @Test
    void verifyRegistrationOtp_doesNotShareLockoutOrCodeWithLoginOtp() {
        otpBacking.put("otp:new@example.com", "654321");

        assertThatThrownBy(() -> authService.verifyRegistrationOtp("new@example.com", "654321"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid or expired");
    }
}

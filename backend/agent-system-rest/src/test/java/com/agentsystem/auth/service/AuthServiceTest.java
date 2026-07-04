package com.agentsystem.auth.service;

import com.agentsystem.auth.service.impl.AuthServiceImpl;

import com.agentsystem.auth.AuthProperties;
import com.agentsystem.notification.NotificationClient;
import com.agentsystem.org.service.OrganizationService;
import com.agentsystem.user.entity.User;
import com.agentsystem.user.entity.UserStatus;
import com.agentsystem.user.service.UserAccountService;
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

    @Mock UserAccountService              userAccountService;
    @Mock StringRedisTemplate             redisTemplate;
    @Mock ValueOperations<String, String> valueOperations;
    @Mock NotificationClient               notificationClient;
    @Mock JwtService                      jwtService;
    @Mock OrganizationService             orgService;

    // AuthProperties is a record (final) — instantiate directly
    private final AuthProperties authProperties = new AuthProperties(
            true, 10, "test-jwt-secret-32-chars-xxxxxxxxx", 24,
            "dGVzdC1lbWFpbC1lbmNyeXB0aW9uLWtleS0zMmJ5dGVz");

    AuthService authService;

    /** otp:<email> / register-otp:<email> → code, backing the mocked StringRedisTemplate below. */
    Map<String, String> otpBacking;
    /** otp:attempts:<email> / register-otp:attempts:<email> → failed-attempt count. */
    Map<String, Long> attemptsBacking;

    private static User activeUser(String email) {
        return new User("uuid-" + email, email, UserStatus.USER, true);
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

        authService = new AuthServiceImpl(authProperties, userAccountService, redisTemplate, notificationClient, jwtService, orgService);
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
                .thenReturn(Optional.of(new User("uuid-1", "pending@example.com", UserStatus.PRE_USER, true)));

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
        User user = activeUser("user@example.com");
        when(userAccountService.findActiveUser("user@example.com")).thenReturn(Optional.of(user));
        when(jwtService.generate(user.getUuid(), "PERSONAL", null)).thenReturn("signed-jwt");

        String token = authService.verifyOtp("user@example.com", "123456");

        assertThat(token).isEqualTo("signed-jwt");
        assertThat(otpBacking).doesNotContainKey("otp:user@example.com");
    }

    @Test
    void verifyOtp_teamMode_validMember_returnsJwt() {
        otpBacking.put("otp:user@example.com", "123456");
        User user = activeUser("user@example.com");
        when(orgService.isMember("skyproton", "user@example.com")).thenReturn(true);
        when(userAccountService.findActiveUser("user@example.com")).thenReturn(Optional.of(user));
        when(jwtService.generate(user.getUuid(), "TEAM", "skyproton")).thenReturn("team-jwt");

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

    @Test
    void verifyOtp_noLongerActive_throwsIllegalArgument() {
        otpBacking.put("otp:user@example.com", "123456");
        when(userAccountService.findActiveUser("user@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.verifyOtp("user@example.com", "123456"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no longer active");
    }

    @Test
    void verifyOtp_successResetsAttemptCounter() {
        otpBacking.put("otp:user@example.com", "123456");
        User user = activeUser("user@example.com");
        when(userAccountService.findActiveUser("user@example.com")).thenReturn(Optional.of(user));
        when(jwtService.generate(user.getUuid(), "PERSONAL", null)).thenReturn("signed-jwt");

        authService.verifyOtp("user@example.com", "123456");

        assertThat(attemptsBacking).doesNotContainKey("otp:attempts:user@example.com");
    }

    @Test
    void verifyOtp_tooManyWrongAttempts_locksOutEvenWithCorrectCode() {
        otpBacking.put("otp:user@example.com", "123456");

        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> authService.verifyOtp("user@example.com", "999999"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid or expired");
        }

        // 6th attempt is locked out regardless of whether the code is now correct.
        assertThatThrownBy(() -> authService.verifyOtp("user@example.com", "123456"))
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
        User pending = new User("uuid-2", "new@example.com", UserStatus.PRE_USER, true);
        when(userAccountService.registerOrGetPending("new@example.com")).thenReturn(pending);

        UserStatus status = authService.verifyRegistrationOtp("new@example.com", "123456");

        assertThat(status).isEqualTo(UserStatus.PRE_USER);
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

    // ── validateToken ─────────────────────────────────────────────────────────

    @Test
    void validateToken_resolvesEmailFromUuidViaUserAccountService() {
        when(jwtService.validateFull("my-token")).thenReturn(new JwtService.TokenClaims("uuid-1", "PERSONAL", null));
        when(userAccountService.getEmailByUuid("uuid-1")).thenReturn("user@example.com");

        assertThat(authService.validateToken("my-token")).isEqualTo("user@example.com");
    }

    @Test
    void validateToken_invalidToken_returnsNull() {
        when(jwtService.validateFull("bad-token")).thenReturn(null);

        assertThat(authService.validateToken("bad-token")).isNull();
    }

    @Test
    void validateTokenFull_delegatesToJwtService() {
        JwtService.TokenClaims claims = new JwtService.TokenClaims("uuid-1", "TEAM", "acme");
        when(jwtService.validateFull("my-token")).thenReturn(claims);

        assertThat(authService.validateTokenFull("my-token")).isSameAs(claims);
    }
}

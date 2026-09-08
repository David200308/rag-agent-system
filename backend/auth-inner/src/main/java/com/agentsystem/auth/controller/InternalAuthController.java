package com.agentsystem.auth.controller;

import com.agentsystem.auth.AuthProperties;
import com.agentsystem.auth.org.OrgLookupService;
import com.agentsystem.auth.service.AuthService;
import com.agentsystem.auth.service.CliKeyService;
import com.agentsystem.auth.service.JwtService;
import com.agentsystem.auth.user.AuthUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Internal-only API consumed by agent-system-rest's AuthInnerClient and the Go
 * scheduler service. Auth: shared {@code X-Auth-Key} header — NOT a user JWT.
 */
@Slf4j
@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
public class InternalAuthController {

    private final AuthService      authService;
    private final JwtService       jwtService;
    private final CliKeyService    cliKeyService;
    private final OrgLookupService orgLookupService;
    private final AuthUserService  userAccountService;
    private final AuthProperties   authProperties;

    // ── OTP: login ────────────────────────────────────────────────────────────

    @PostMapping("/otp/login/request")
    public ResponseEntity<Map<String, String>> requestLoginOtp(
            @RequestHeader(value = "X-Auth-Key", required = false) String key,
            @RequestBody Map<String, String> body) {
        if (!validKey(key)) return ResponseEntity.status(401).build();

        String email = body.get("email");
        if (email == null || email.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "email is required"));
        }
        try {
            authService.requestOtp(email);
            return ResponseEntity.ok(Map.of("message", "Code sent to " + email));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("[InternalAuthController] requestLoginOtp error: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to send code. Please try again."));
        }
    }

    @PostMapping("/otp/login/verify")
    public ResponseEntity<Map<String, String>> verifyLoginOtp(
            @RequestHeader(value = "X-Auth-Key", required = false) String key,
            @RequestBody Map<String, String> body) {
        if (!validKey(key)) return ResponseEntity.status(401).build();

        String email = body.get("email");
        String code  = body.get("code");
        String mode  = body.getOrDefault("mode", "PERSONAL");
        String orgId = body.get("orgId");
        if (email == null || email.isBlank() || code == null || code.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "email and code are required"));
        }
        try {
            String jwt = authService.verifyOtp(email, code, mode, orgId);
            return ResponseEntity.ok(Map.of("token", jwt));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(401).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("[InternalAuthController] verifyLoginOtp error: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Verification failed. Please try again."));
        }
    }

    // ── OTP: registration ────────────────────────────────────────────────────

    @PostMapping("/otp/register/request")
    public ResponseEntity<Map<String, String>> requestRegisterOtp(
            @RequestHeader(value = "X-Auth-Key", required = false) String key,
            @RequestBody Map<String, String> body) {
        if (!validKey(key)) return ResponseEntity.status(401).build();

        String email = body.get("email");
        if (email == null || email.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "email is required"));
        }
        try {
            authService.requestRegistrationOtp(email);
            return ResponseEntity.ok(Map.of("message", "Code sent to " + email));
        } catch (Exception e) {
            log.error("[InternalAuthController] requestRegisterOtp error: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to send code. Please try again."));
        }
    }

    @PostMapping("/otp/register/verify")
    public ResponseEntity<Map<String, String>> verifyRegisterOtp(
            @RequestHeader(value = "X-Auth-Key", required = false) String key,
            @RequestBody Map<String, String> body) {
        if (!validKey(key)) return ResponseEntity.status(401).build();

        String email = body.get("email");
        String code  = body.get("code");
        if (email == null || email.isBlank() || code == null || code.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "email and code are required"));
        }
        try {
            String status = authService.verifyRegistrationOtp(email, code);
            return ResponseEntity.ok(Map.of("status", status));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(401).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("[InternalAuthController] verifyRegisterOtp error: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Verification failed. Please try again."));
        }
    }

    // ── Validate JWT ──────────────────────────────────────────────────────────

    /**
     * Public response shape ({@code valid}/{@code email}/{@code mode}/{@code orgId}) matches
     * what agent-system-rest's public {@code GET /api/v1/auth/validate} returned before this
     * migration, so the Go scheduler needed only a URL + header change, not a code change.
     */
    @GetMapping("/validate")
    public ResponseEntity<Map<String, Object>> validate(
            @RequestHeader(value = "X-Auth-Key", required = false) String key,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (!validKey(key)) return ResponseEntity.status(401).build();

        String token  = extractToken(authHeader);
        var    claims = (token != null) ? jwtService.validateFull(token) : null;

        if (claims == null) {
            return ResponseEntity.ok(Map.of("valid", false));
        }
        Map<String, Object> result = new HashMap<>();
        result.put("valid", true);
        result.put("userUuid", claims.userUuid());
        result.put("email", userAccountService.getEmailByUuid(claims.userUuid()));
        result.put("mode",  claims.mode());
        if (claims.orgId() != null) result.put("orgId", claims.orgId());
        return ResponseEntity.ok(result);
    }

    // ── CLI key ───────────────────────────────────────────────────────────────

    @PostMapping("/cli-key/register")
    public ResponseEntity<Map<String, String>> registerCliKey(
            @RequestHeader(value = "X-Auth-Key", required = false) String key,
            @RequestBody Map<String, String> body) {
        if (!validKey(key)) return ResponseEntity.status(401).build();

        String email     = body.get("email");
        String publicKey = body.get("publicKey");
        if (email == null || email.isBlank() || publicKey == null || publicKey.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "email and publicKey are required"));
        }
        try {
            String fingerprint = cliKeyService.registerKey(email, publicKey);
            return ResponseEntity.ok(Map.of("message", "CLI key registered", "fingerprint", fingerprint));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/cli-signature/verify")
    public ResponseEntity<Map<String, Boolean>> verifyCliSignature(
            @RequestHeader(value = "X-Auth-Key", required = false) String key,
            @RequestBody Map<String, Object> body) {
        if (!validKey(key)) return ResponseEntity.status(401).build();

        boolean valid = cliKeyService.verify(
                (String) body.get("email"),
                (String) body.get("signature"),
                (String) body.get("cliVersion"),
                (String) body.get("method"),
                (String) body.get("path"),
                ((Number) body.get("timestamp")).longValue());
        return ResponseEntity.ok(Map.of("valid", valid));
    }

    // ── Org existence (backs the public /api/v1/auth/org/{orgId} check) ────────

    @GetMapping("/org/{orgId}/exists")
    public ResponseEntity<Map<String, Boolean>> orgExists(
            @RequestHeader(value = "X-Auth-Key", required = false) String key,
            @PathVariable String orgId) {
        if (!validKey(key)) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(Map.of("exists", orgLookupService.orgExists(orgId)));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private boolean validKey(String key) {
        return key != null && key.equals(authProperties.serviceKey());
    }

    private String extractToken(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }
}

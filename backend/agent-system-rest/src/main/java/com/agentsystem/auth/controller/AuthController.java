package com.agentsystem.auth.controller;

import com.agentsystem.auth.service.AuthService;
import com.agentsystem.auth.service.CliKeyService;
import com.agentsystem.org.service.OrganizationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Auth endpoints — always public (excluded from AuthFilter).
 *
 *  POST /api/v1/auth/request-otp  — send OTP to whitelisted email
 *  POST /api/v1/auth/verify-otp   — validate OTP, return signed JWT
 *  POST /api/v1/auth/logout        — client-side only (JWT is stateless)
 *  GET  /api/v1/auth/validate      — check if a JWT is still valid
 *  POST /api/v1/auth/register-key  — register CLI Ed25519 public key (JWT required)
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Email OTP login endpoints (JWT)")
public class AuthController {

    private final AuthService         authService;
    private final CliKeyService       cliKeyService;
    private final OrganizationService orgService;

    // ── Request OTP ──────────────────────────────────────────────────────────────

    @PostMapping("/request-otp")
    @Operation(summary = "Send a 6-digit OTP to a whitelisted email address")
    public ResponseEntity<Map<String, String>> requestOtp(
            @RequestBody Map<String, String> body) {

        String email = body.get("email");
        if (email == null || email.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "email is required"));
        }

        try {
            authService.requestOtp(email);
            return ResponseEntity.ok(Map.of("message", "Code sent to " + email));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(403)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("[AuthController] requestOtp error: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to send code. Please try again."));
        }
    }

    // ── Verify OTP ───────────────────────────────────────────────────────────────

    @PostMapping("/verify-otp")
    @Operation(summary = "Verify the OTP and receive a signed JWT")
    public ResponseEntity<Map<String, String>> verifyOtp(
            @RequestBody Map<String, String> body) {

        String email  = body.get("email");
        String code   = body.get("code");
        String mode   = body.getOrDefault("mode", "PERSONAL");
        String orgId  = body.get("orgId");

        if (email == null || email.isBlank() || code == null || code.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "email and code are required"));
        }

        try {
            String jwt = authService.verifyOtp(email, code, mode, orgId);
            return ResponseEntity.ok(Map.of("token", jwt));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(401)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("[AuthController] verifyOtp error: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Verification failed. Please try again."));
        }
    }

    // ── Logout ───────────────────────────────────────────────────────────────────

    @PostMapping("/logout")
    @Operation(summary = "Logout — JWT is stateless; client discards the token")
    public ResponseEntity<Map<String, String>> logout() {
        // JWT tokens cannot be server-side revoked without a denylist.
        // The Next.js layer clears the httpOnly cookie on logout.
        return ResponseEntity.ok(Map.of("message", "Logged out"));
    }

    // ── Validate ─────────────────────────────────────────────────────────────────

    @GetMapping("/validate")
    @Operation(summary = "Check whether a JWT is valid")
    public ResponseEntity<Map<String, Object>> validate(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        String token = extractToken(authHeader);
        var claims = (token != null) ? authService.validateTokenFull(token) : null;

        if (claims == null) {
            return ResponseEntity.ok(Map.of("valid", false));
        }
        java.util.Map<String, Object> result = new java.util.HashMap<>();
        result.put("valid", true);
        result.put("email", claims.email());
        result.put("mode",  claims.mode());
        if (claims.orgId() != null) result.put("orgId", claims.orgId());
        return ResponseEntity.ok(result);
    }

    // ── Register CLI public key ───────────────────────────────────────────────────

    @PostMapping("/register-key")
    @Operation(summary = "Register an Ed25519 public key for CLI request signing (JWT required)")
    public ResponseEntity<Map<String, String>> registerKey(
            @RequestBody Map<String, String> body,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        // /api/v1/auth/* is exempt from AuthFilter, so validate the JWT manually
        // (same pattern as the /validate endpoint above)
        String token = extractToken(authHeader);
        String email = (token != null) ? authService.validateToken(token) : null;
        if (email == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }

        String publicKey = body.get("publicKey");
        if (publicKey == null || publicKey.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "publicKey is required"));
        }

        try {
            String fingerprint = cliKeyService.registerKey(email, publicKey);
            return ResponseEntity.ok(Map.of(
                    "message",     "CLI key registered",
                    "fingerprint", fingerprint
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ── Check org (used at login — no auth required) ──────────────────────────

    @GetMapping("/org/{orgId}")
    @Operation(summary = "Check whether an org slug exists (called at team login)")
    public ResponseEntity<Map<String, Object>> checkOrg(@PathVariable String orgId) {
        boolean exists = isOrgRegistered(orgId);
        return ResponseEntity.ok(Map.of("exists", exists));
    }

    // ── Helper ───────────────────────────────────────────────────────────────────

    private boolean isOrgRegistered(String orgId) {
        try {
            orgService.requireOrgExists(orgId);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private String extractToken(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }
}

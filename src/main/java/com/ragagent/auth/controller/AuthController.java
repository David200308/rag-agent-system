package com.ragagent.auth.controller;

import com.ragagent.auth.service.AuthService;
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
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Email OTP login endpoints (JWT)")
public class AuthController {

    private final AuthService authService;

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

        String email = body.get("email");
        String code  = body.get("code");

        if (email == null || email.isBlank() || code == null || code.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "email and code are required"));
        }

        try {
            String jwt = authService.verifyOtp(email, code);
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
        String email = (token != null) ? authService.validateToken(token) : null;

        if (email == null) {
            return ResponseEntity.ok(Map.of("valid", false));
        }
        return ResponseEntity.ok(Map.of("valid", true, "email", email));
    }

    // ── Helper ───────────────────────────────────────────────────────────────────

    private String extractToken(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }
}

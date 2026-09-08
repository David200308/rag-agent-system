package com.agentsystem.user.controller;

import com.agentsystem.auth.AuthInnerClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Authenticated passkey management endpoints (requires JWT via AuthFilter). Thin proxy
 * over auth-inner's internal passkey API.
 *
 *  POST   /api/v1/user/passkey/register/begin   — start passkey setup
 *  POST   /api/v1/user/passkey/register/finish  — complete passkey setup
 *  DELETE /api/v1/user/passkey                  — remove all passkeys
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/user/passkey")
@RequiredArgsConstructor
@Tag(name = "Passkey Management", description = "Passkey setup and removal for authenticated users")
public class PasskeyUserController {

    private final AuthInnerClient authInnerClient;

    @PostMapping("/register/begin")
    @Operation(summary = "Start passkey registration — returns WebAuthn creation options")
    public ResponseEntity<Object> registerBegin(HttpServletRequest req) {
        String email = (String) req.getAttribute("authenticatedEmail");
        if (email == null) return ResponseEntity.status(401).build();
        try {
            return ResponseEntity.ok(authInnerClient.passkeyRegisterBegin(email));
        } catch (Exception e) {
            log.error("[PasskeyUserController] registerBegin error", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to start registration"));
        }
    }

    @PostMapping("/register/finish")
    @Operation(summary = "Complete passkey registration")
    public ResponseEntity<Map<String, String>> registerFinish(
            @RequestBody Map<String, Object> body,
            HttpServletRequest req) {
        String email = (String) req.getAttribute("authenticatedEmail");
        if (email == null) return ResponseEntity.status(401).build();

        Object response = body.get("response");
        if (response == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "response is required"));
        }
        try {
            String responseJson = response instanceof String s ? s : toJson(response);
            authInnerClient.passkeyRegisterFinish(email, responseJson);
            return ResponseEntity.ok(Map.of("message", "Passkey registered successfully"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("[PasskeyUserController] registerFinish error", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Registration failed"));
        }
    }

    @DeleteMapping
    @Operation(summary = "Remove all registered passkeys for the current user")
    public ResponseEntity<Map<String, String>> deletePasskey(HttpServletRequest req) {
        String email = (String) req.getAttribute("authenticatedEmail");
        if (email == null) return ResponseEntity.status(401).build();
        authInnerClient.passkeyDelete(email);
        return ResponseEntity.ok(Map.of("message", "Passkey removed"));
    }

    private String toJson(Object obj) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}

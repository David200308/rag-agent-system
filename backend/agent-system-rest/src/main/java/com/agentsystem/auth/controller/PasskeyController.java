package com.agentsystem.auth.controller;

import com.agentsystem.auth.AuthInnerClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Public passkey endpoints — excluded from AuthFilter (under /api/v1/auth/). Thin proxy
 * over auth-inner's internal passkey API.
 *
 *  GET  /api/v1/auth/passkey/status                       — check if user has a passkey
 *  POST /api/v1/auth/passkey/authenticate/begin           — start passkey login
 *  POST /api/v1/auth/passkey/authenticate/finish          — finish passkey login, returns JWT
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth/passkey")
@RequiredArgsConstructor
@io.swagger.v3.oas.annotations.tags.Tag(name = "Passkey Auth", description = "WebAuthn passkey authentication endpoints")
public class PasskeyController {

    private final AuthInnerClient authInnerClient;

    @GetMapping("/status")
    @io.swagger.v3.oas.annotations.Operation(summary = "Check whether the given email has a registered passkey")
    public ResponseEntity<Map<String, Boolean>> status(@RequestParam String email) {
        if (email == null || email.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        boolean has = authInnerClient.passkeyStatus(email.trim().toLowerCase());
        return ResponseEntity.ok(Map.of("hasPasskey", has));
    }

    @PostMapping("/authenticate/begin")
    @io.swagger.v3.oas.annotations.Operation(summary = "Start passkey authentication — returns WebAuthn request options")
    public ResponseEntity<Object> authenticateBegin(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        if (email == null || email.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "email is required"));
        }
        String mode  = body.getOrDefault("mode", "PERSONAL");
        String orgId = body.get("orgId");
        try {
            return ResponseEntity.ok(
                    authInnerClient.passkeyAuthenticateBegin(email.trim().toLowerCase(), mode, orgId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("[PasskeyController] authenticateBegin error", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to start authentication"));
        }
    }

    @PostMapping("/authenticate/finish")
    @io.swagger.v3.oas.annotations.Operation(summary = "Finish passkey authentication — returns signed JWT on success")
    public ResponseEntity<Map<String, String>> authenticateFinish(@RequestBody Map<String, Object> body) {
        String email = (String) body.get("email");
        Object response = body.get("response");
        if (email == null || email.isBlank() || response == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "email and response are required"));
        }
        try {
            String responseJson = response instanceof String s ? s : toJson(response);
            String jwt = authInnerClient.passkeyAuthenticateFinish(email.trim().toLowerCase(), responseJson);
            return ResponseEntity.ok(Map.of("token", jwt));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(401).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("[PasskeyController] authenticateFinish error", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Authentication failed"));
        }
    }

    private String toJson(Object obj) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}

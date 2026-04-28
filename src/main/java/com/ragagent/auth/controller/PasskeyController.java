package com.ragagent.auth.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ragagent.auth.service.PasskeyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Public passkey endpoints — excluded from AuthFilter (under /api/v1/auth/).
 *
 *  GET  /api/v1/auth/passkey/status                       — check if user has a passkey
 *  POST /api/v1/auth/passkey/authenticate/begin           — start passkey login
 *  POST /api/v1/auth/passkey/authenticate/finish          — finish passkey login, returns JWT
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth/passkey")
@RequiredArgsConstructor
@Tag(name = "Passkey Auth", description = "WebAuthn passkey authentication endpoints")
public class PasskeyController {

    private final PasskeyService passkeyService;
    private final ObjectMapper   objectMapper;

    @GetMapping("/status")
    @Operation(summary = "Check whether the given email has a registered passkey")
    public ResponseEntity<Map<String, Boolean>> status(@RequestParam String email) {
        if (email == null || email.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        boolean has = passkeyService.hasPasskey(email.trim().toLowerCase());
        return ResponseEntity.ok(Map.of("hasPasskey", has));
    }

    @PostMapping("/authenticate/begin")
    @Operation(summary = "Start passkey authentication — returns WebAuthn request options")
    public ResponseEntity<Object> authenticateBegin(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        if (email == null || email.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "email is required"));
        }
        try {
            String optionsJson = passkeyService.startAuthentication(email.trim().toLowerCase());
            JsonNode node = objectMapper.readTree(optionsJson);
            return ResponseEntity.ok(node);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("[PasskeyController] authenticateBegin error", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to start authentication"));
        }
    }

    @PostMapping("/authenticate/finish")
    @Operation(summary = "Finish passkey authentication — returns signed JWT on success")
    public ResponseEntity<Map<String, String>> authenticateFinish(@RequestBody Map<String, Object> body) {
        String email = (String) body.get("email");
        Object response = body.get("response");
        if (email == null || email.isBlank() || response == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "email and response are required"));
        }
        try {
            String responseJson = response instanceof String s ? s : toJson(response);
            String jwt = passkeyService.finishAuthentication(email.trim().toLowerCase(), responseJson);
            return ResponseEntity.ok(Map.of("token", jwt));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.status(401).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("[PasskeyController] authenticateFinish error", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Authentication failed"));
        }
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}

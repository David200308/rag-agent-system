package com.agentsystem.auth.controller;

import com.agentsystem.auth.AuthProperties;
import com.agentsystem.auth.service.PasskeyService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Internal-only passkey API consumed by agent-system-rest's AuthInnerClient.
 * Auth: shared {@code X-Auth-Key} header — NOT a user JWT. The caller (agent-system-rest)
 * has already resolved and authorized the email before calling register/finish/delete.
 */
@Slf4j
@RestController
@RequestMapping("/internal/passkey")
@RequiredArgsConstructor
public class InternalPasskeyController {

    private final PasskeyService passkeyService;
    private final ObjectMapper   objectMapper;
    private final AuthProperties authProperties;

    @GetMapping("/status")
    public ResponseEntity<Map<String, Boolean>> status(
            @RequestHeader(value = "X-Auth-Key", required = false) String key,
            @RequestParam String email) {
        if (!validKey(key)) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(Map.of("hasPasskey", passkeyService.hasPasskey(email)));
    }

    @PostMapping("/authenticate/begin")
    public ResponseEntity<Object> authenticateBegin(
            @RequestHeader(value = "X-Auth-Key", required = false) String key,
            @RequestBody Map<String, String> body) throws Exception {
        if (!validKey(key)) return ResponseEntity.status(401).build();

        String email = body.get("email");
        String mode  = body.getOrDefault("mode", "PERSONAL");
        String orgId = body.get("orgId");
        try {
            String optionsJson = passkeyService.startAuthentication(email, mode, orgId);
            JsonNode node = objectMapper.readTree(optionsJson);
            return ResponseEntity.ok(node);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("[InternalPasskeyController] authenticateBegin error", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to start authentication"));
        }
    }

    @PostMapping("/authenticate/finish")
    public ResponseEntity<Map<String, String>> authenticateFinish(
            @RequestHeader(value = "X-Auth-Key", required = false) String key,
            @RequestBody Map<String, Object> body) throws Exception {
        if (!validKey(key)) return ResponseEntity.status(401).build();

        String email    = (String) body.get("email");
        String response = (String) body.get("response");
        try {
            String jwt = passkeyService.finishAuthentication(email, response);
            return ResponseEntity.ok(Map.of("token", jwt));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.status(401).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("[InternalPasskeyController] authenticateFinish error", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Authentication failed"));
        }
    }

    @PostMapping("/register/begin")
    public ResponseEntity<Object> registerBegin(
            @RequestHeader(value = "X-Auth-Key", required = false) String key,
            @RequestBody Map<String, String> body) throws Exception {
        if (!validKey(key)) return ResponseEntity.status(401).build();

        try {
            String optionsJson = passkeyService.startRegistration(body.get("email"));
            JsonNode node = objectMapper.readTree(optionsJson);
            return ResponseEntity.ok(node);
        } catch (Exception e) {
            log.error("[InternalPasskeyController] registerBegin error", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to start registration"));
        }
    }

    @PostMapping("/register/finish")
    public ResponseEntity<Map<String, String>> registerFinish(
            @RequestHeader(value = "X-Auth-Key", required = false) String key,
            @RequestBody Map<String, Object> body) throws Exception {
        if (!validKey(key)) return ResponseEntity.status(401).build();

        String email    = (String) body.get("email");
        String response = (String) body.get("response");
        try {
            passkeyService.finishRegistration(email, response);
            return ResponseEntity.ok(Map.of("message", "Passkey registered successfully"));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("[InternalPasskeyController] registerFinish error", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Registration failed"));
        }
    }

    @DeleteMapping
    public ResponseEntity<Map<String, String>> delete(
            @RequestHeader(value = "X-Auth-Key", required = false) String key,
            @RequestParam String email) {
        if (!validKey(key)) return ResponseEntity.status(401).build();
        passkeyService.deletePasskeys(email);
        return ResponseEntity.ok(Map.of("message", "Passkey removed"));
    }

    private boolean validKey(String key) {
        return key != null && key.equals(authProperties.serviceKey());
    }
}

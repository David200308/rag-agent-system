package com.agentsystem.auth;

import com.agentsystem.config.AuthInnerProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Calls auth-inner's internal REST API on behalf of authenticated users.
 * Uses X-Auth-Key authentication — no JWT needed (mirrors StorageClient).
 *
 * auth-inner returns {@code {"error": "..."}} with a 4xx status for business-rule
 * failures (bad OTP, unknown org, etc.) — this client normalises every 4xx into an
 * {@link IllegalArgumentException} carrying that message, matching the exception type
 * the old in-process AuthService/PasskeyService/CliKeyService used to throw, so the
 * public controllers' existing per-endpoint catch blocks (and their status codes)
 * don't need to change. A 5xx (or connection failure) propagates as a plain
 * RuntimeException, which those controllers' generic {@code catch (Exception e)} already
 * turns into a 500.
 */
@Slf4j
@Component
public class AuthInnerClient {

    private final RestClient  restClient;
    private final ObjectMapper objectMapper;

    public AuthInnerClient(AuthInnerProperties props, ObjectMapper objectMapper) {
        this.restClient = RestClient.builder()
                .baseUrl(props.url())
                .defaultHeader("X-Auth-Key", props.serviceKey())
                .build();
        this.objectMapper = objectMapper;
    }

    public record ValidateResult(boolean valid, String userUuid, String email, String mode, String orgId) {}

    // ── Validate ─────────────────────────────────────────────────────────────────

    public ValidateResult validate(String bearerToken) {
        JsonNode node = restClient.get()
                .uri("/internal/validate")
                .header("Authorization", bearerToken != null ? "Bearer " + bearerToken : "")
                .retrieve()
                .body(JsonNode.class);
        if (node == null || !node.path("valid").asBoolean(false)) {
            return new ValidateResult(false, null, null, null, null);
        }
        return new ValidateResult(true,
                node.path("userUuid").asText(null),
                node.path("email").asText(null),
                node.path("mode").asText(null),
                node.hasNonNull("orgId") ? node.path("orgId").asText() : null);
    }

    // ── OTP: login ────────────────────────────────────────────────────────────

    public void requestLoginOtp(String email) {
        post("/internal/otp/login/request", Map.of("email", email), Map.class);
    }

    public String verifyLoginOtp(String email, String code, String mode, String orgId) {
        Map<String, String> body = new java.util.HashMap<>();
        body.put("email", email);
        body.put("code", code);
        body.put("mode", mode);
        if (orgId != null) body.put("orgId", orgId);
        Map<?, ?> result = post("/internal/otp/login/verify", body, Map.class);
        return (String) result.get("token");
    }

    // ── OTP: registration ────────────────────────────────────────────────────

    public void requestRegisterOtp(String email) {
        post("/internal/otp/register/request", Map.of("email", email), Map.class);
    }

    public String verifyRegisterOtp(String email, String code) {
        Map<?, ?> result = post("/internal/otp/register/verify", Map.of("email", email, "code", code), Map.class);
        return (String) result.get("status");
    }

    // ── CLI key ───────────────────────────────────────────────────────────────

    public String registerCliKey(String email, String publicKey) {
        Map<?, ?> result = post("/internal/cli-key/register",
                Map.of("email", email, "publicKey", publicKey), Map.class);
        return (String) result.get("fingerprint");
    }

    public boolean verifyCliSignature(String email, String signature, String cliVersion,
                                       String method, String path, long timestamp) {
        Map<String, Object> body = Map.of(
                "email", email, "signature", signature, "cliVersion", cliVersion,
                "method", method, "path", path, "timestamp", timestamp);
        Map<?, ?> result = post("/internal/cli-signature/verify", body, Map.class);
        return Boolean.TRUE.equals(result.get("valid"));
    }

    // ── Org existence ─────────────────────────────────────────────────────────

    public boolean orgExists(String orgId) {
        try {
            Map<?, ?> result = restClient.get()
                    .uri("/internal/org/{orgId}/exists", orgId)
                    .retrieve()
                    .body(Map.class);
            return result != null && Boolean.TRUE.equals(result.get("exists"));
        } catch (HttpClientErrorException e) {
            return false;
        }
    }

    // ── Passkey ───────────────────────────────────────────────────────────────

    public boolean passkeyStatus(String email) {
        Map<?, ?> result = restClient.get()
                .uri("/internal/passkey/status?email={email}", email)
                .retrieve()
                .body(Map.class);
        return result != null && Boolean.TRUE.equals(result.get("hasPasskey"));
    }

    public JsonNode passkeyAuthenticateBegin(String email, String mode, String orgId) {
        Map<String, String> body = new java.util.HashMap<>();
        body.put("email", email);
        body.put("mode", mode);
        if (orgId != null) body.put("orgId", orgId);
        return post("/internal/passkey/authenticate/begin", body, JsonNode.class);
    }

    public String passkeyAuthenticateFinish(String email, String responseJson) {
        Map<?, ?> result = post("/internal/passkey/authenticate/finish",
                Map.of("email", email, "response", responseJson), Map.class);
        return (String) result.get("token");
    }

    public JsonNode passkeyRegisterBegin(String email) {
        return post("/internal/passkey/register/begin", Map.of("email", email), JsonNode.class);
    }

    public void passkeyRegisterFinish(String email, String responseJson) {
        post("/internal/passkey/register/finish", Map.of("email", email, "response", responseJson), Map.class);
    }

    public void passkeyDelete(String email) {
        restClient.delete()
                .uri("/internal/passkey?email={email}", email)
                .retrieve()
                .toBodilessEntity();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────

    private <T> T post(String uri, Object body, Class<T> responseType) {
        try {
            return restClient.post()
                    .uri(uri)
                    .body(body)
                    .retrieve()
                    .body(responseType);
        } catch (HttpClientErrorException e) {
            throw new IllegalArgumentException(extractError(e), e);
        }
    }

    private String extractError(HttpClientErrorException e) {
        try {
            JsonNode node = objectMapper.readTree(e.getResponseBodyAsByteArray());
            String msg = node.path("error").asText(null);
            return msg != null ? msg : e.getStatusText();
        } catch (Exception parseError) {
            return e.getStatusText();
        }
    }
}

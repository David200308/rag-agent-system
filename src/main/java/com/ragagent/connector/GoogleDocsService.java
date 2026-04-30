package com.ragagent.connector;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Creates Google Docs documents using the stored OAuth token.
 *
 * Flow:
 *  1. Load token for the user from DB; refresh if expiring.
 *  2. POST https://docs.googleapis.com/v1/documents  → get documentId.
 *  3. POST batchUpdate to insert the text content.
 *  4. Return the public edit URL.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GoogleDocsService {

    private static final String DOCS_BASE     = "https://docs.googleapis.com/v1/documents";
    private static final String TOKEN_URL     = "https://oauth2.googleapis.com/token";
    private static final int    REFRESH_SECS  = 300;  // refresh when < 5 min remaining

    private final ConnectorTokenRepository  tokenRepo;
    private final ConnectorProperties       props;
    private final RestClient.Builder        restClientBuilder;

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Create a new Google Doc with the given title and plain-text content.
     *
     * @param title      document title
     * @param content    body text (may contain newlines)
     * @param ownerEmail the user whose token to use; {@code ""} when auth is disabled
     * @return the document's browser URL (https://docs.google.com/document/d/{id}/edit)
     */
    public String createDocument(String title, String content, String ownerEmail) {
        String email = ownerEmail != null ? ownerEmail : "";
        String token = resolveAccessToken(email);

        // ── Step 1: create an empty document ────────────────────────────────
        CreateDocResponse created = restClientBuilder.build()
                .post()
                .uri(DOCS_BASE)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("title", title))
                .retrieve()
                .body(CreateDocResponse.class);

        if (created == null || created.documentId() == null) {
            throw new IllegalStateException("Google Docs API returned no documentId");
        }
        String docId = created.documentId();
        log.info("[GoogleDocsService] Created doc {} for {}", docId, email);

        // ── Step 2: insert content via batchUpdate ───────────────────────────
        // The document starts with one empty paragraph at index 0..1.
        // Inserting at index 1 places our text just before the trailing newline.
        if (content != null && !content.isBlank()) {
            Map<String, Object> batchBody = Map.of(
                    "requests", List.of(Map.of(
                            "insertText", Map.of(
                                    "location", Map.of("index", 1),
                                    "text", content
                            )
                    ))
            );
            restClientBuilder.build()
                    .post()
                    .uri(DOCS_BASE + "/" + docId + ":batchUpdate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + token)
                    .body(batchBody)
                    .retrieve()
                    .toBodilessEntity();
        }

        String url = "https://docs.google.com/document/d/" + docId + "/edit";
        log.info("[GoogleDocsService] Document ready: {}", url);
        return url;
    }

    /** Returns true if the user has a valid (possibly refreshable) Google token. */
    public boolean isConnected(String ownerEmail) {
        String email = ownerEmail != null ? ownerEmail : "";
        return tokenRepo.findByOwnerEmailAndProvider(email, "google").isPresent();
    }

    // ── Token management ──────────────────────────────────────────────────────

    private String resolveAccessToken(String email) {
        ConnectorToken ct = tokenRepo.findByOwnerEmailAndProvider(email, "google")
                .or(() -> email.isEmpty() ? java.util.Optional.empty()
                        : tokenRepo.findByOwnerEmailAndProvider("", "google"))
                .orElseThrow(() -> new IllegalStateException(
                        "Google account not connected. Visit /mcp to connect."));

        if (isExpiringSoon(ct)) {
            ct = refreshToken(ct);
        }
        return ct.getAccessToken();
    }

    private boolean isExpiringSoon(ConnectorToken ct) {
        return ct.getExpiresAt() != null
                && ct.getExpiresAt().isBefore(LocalDateTime.now().plusSeconds(REFRESH_SECS));
    }

    private ConnectorToken refreshToken(ConnectorToken ct) {
        if (ct.getRefreshToken() == null) {
            log.warn("[GoogleDocsService] No refresh token stored — using potentially expired access token");
            return ct;
        }

        log.info("[GoogleDocsService] Refreshing Google token for {}", ct.getOwnerEmail());

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id",     props.google().clientId());
        form.add("client_secret", props.google().clientSecret());
        form.add("refresh_token", ct.getRefreshToken());
        form.add("grant_type",    "refresh_token");

        RefreshResponse refreshed = restClientBuilder.build()
                .post()
                .uri(TOKEN_URL)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(RefreshResponse.class);

        if (refreshed == null || refreshed.accessToken() == null) {
            throw new IllegalStateException("Google token refresh failed");
        }

        ct.setAccessToken(refreshed.accessToken());
        if (refreshed.expiresIn() != null) {
            ct.setExpiresAt(LocalDateTime.now().plusSeconds(refreshed.expiresIn()));
        }
        return tokenRepo.save(ct);
    }

    // ── DTOs ─────────────────────────────────────────────────────────────────

    @JsonIgnoreProperties(ignoreUnknown = true)
    record CreateDocResponse(
            @JsonProperty("documentId") String documentId,
            @JsonProperty("title")      String title
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RefreshResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("expires_in")   Long   expiresIn
    ) {}
}

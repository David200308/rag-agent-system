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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Creates Google Slides presentations using the stored OAuth token.
 *
 * Content format: slides separated by "---" on its own line.
 * First line of each slide block is treated as the slide title;
 * remaining lines become the body text.
 *
 * Flow:
 *  1. Load + refresh token.
 *  2. POST https://slides.googleapis.com/v1/presentations  → presentationId.
 *  3. POST batchUpdate to add slides with title + body text boxes.
 *  4. Return the presentation URL.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GoogleSlidesService {

    private static final String SLIDES_BASE  = "https://slides.googleapis.com/v1/presentations";
    private static final String TOKEN_URL    = "https://oauth2.googleapis.com/token";
    private static final int    REFRESH_SECS = 300;

    private final ConnectorTokenRepository tokenRepo;
    private final ConnectorProperties      props;
    private final RestClient.Builder       restClientBuilder;

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Create a new Google Slides presentation.
     *
     * @param title      presentation title
     * @param content    text content; slides separated by "---"
     * @param ownerEmail user whose token to use
     * @return the presentation's browser URL
     */
    public String createPresentation(String title, String content, String ownerEmail) {
        String email = ownerEmail != null ? ownerEmail : "";
        String token = resolveAccessToken(email);

        // ── Step 1: create empty presentation ────────────────────────────────
        CreatePresentationResponse created = restClientBuilder.build()
                .post()
                .uri(SLIDES_BASE)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("title", title))
                .retrieve()
                .body(CreatePresentationResponse.class);

        if (created == null || created.presentationId() == null) {
            throw new IllegalStateException("Slides API returned no presentationId");
        }
        String presId    = created.presentationId();
        String firstSlideId = created.firstSlideId();
        log.info("[GoogleSlidesService] Created presentation {} for {}", presId, email);

        // ── Step 2: build batchUpdate requests for each slide ─────────────────
        List<Map<String, Object>> requests = buildSlideRequests(content, firstSlideId);
        if (!requests.isEmpty()) {
            restClientBuilder.build()
                    .post()
                    .uri(SLIDES_BASE + "/" + presId + ":batchUpdate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + token)
                    .body(Map.of("requests", requests))
                    .retrieve()
                    .toBodilessEntity();
        }

        String url = "https://docs.google.com/presentation/d/" + presId + "/edit";
        log.info("[GoogleSlidesService] Presentation ready: {}", url);
        return url;
    }

    public boolean isConnected(String ownerEmail) {
        String email = ownerEmail != null ? ownerEmail : "";
        return tokenRepo.findByOwnerEmailAndProvider(email, "google").isPresent();
    }

    // ── Slide building ────────────────────────────────────────────────────────

    private List<Map<String, Object>> buildSlideRequests(String content, String firstSlideId) {
        List<Map<String, Object>> requests = new ArrayList<>();
        if (content == null || content.isBlank()) return requests;

        String[] blocks = content.split("(?m)^---$");
        boolean  first  = true;

        for (String block : blocks) {
            String trimmed = block.strip();
            if (trimmed.isBlank()) continue;

            String[] lines    = trimmed.split("\n", 2);
            String   slideTitle = lines[0].strip();
            String   body       = lines.length > 1 ? lines[1].strip() : "";

            if (first && firstSlideId != null) {
                // Populate the auto-created first slide instead of adding a new one
                requests.addAll(populateFirstSlide(firstSlideId, slideTitle, body));
                first = false;
            } else {
                requests.addAll(addSlide(slideTitle, body));
            }
        }
        return requests;
    }

    /** Populate the title + body placeholders of the first (already-existing) slide. */
    private List<Map<String, Object>> populateFirstSlide(String slideId, String title, String body) {
        // We don't know the placeholder IDs without reading the slide object,
        // so we delete the blank slide and recreate it the same way as others.
        List<Map<String, Object>> reqs = new ArrayList<>();
        reqs.add(Map.of("deleteObject", Map.of("objectId", slideId)));
        reqs.addAll(addSlide(title, body));
        return reqs;
    }

    /** Returns a sequence of batchUpdate requests that add one slide with title + body. */
    private List<Map<String, Object>> addSlide(String title, String body) {
        String slideId     = "slide_"  + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String titleBoxId  = "title_"  + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String bodyBoxId   = "body_"   + UUID.randomUUID().toString().replace("-", "").substring(0, 8);

        List<Map<String, Object>> reqs = new ArrayList<>();

        // 1) Insert a blank slide
        reqs.add(Map.of("insertSlide", Map.of(
                "objectId",        slideId,
                "slideLayoutReference", Map.of("predefinedLayout", "BLANK")
        )));

        // 2) Add title text box
        reqs.add(Map.of("createShape", Map.of(
                "objectId",   titleBoxId,
                "shapeType",  "TEXT_BOX",
                "elementProperties", Map.of(
                        "pageObjectId", slideId,
                        "size", Map.of(
                                "width",  Map.of("magnitude", 6_000_000, "unit", "EMU"),
                                "height", Map.of("magnitude", 800_000,   "unit", "EMU")
                        ),
                        "transform", Map.of(
                                "scaleX", 1, "scaleY", 1,
                                "translateX", 457_200, "translateY", 274_638,
                                "unit", "EMU"
                        )
                )
        )));
        reqs.add(Map.of("insertText", Map.of(
                "objectId", titleBoxId,
                "text",     title
        )));

        // 3) Add body text box (only when there's actual body content)
        if (!body.isBlank()) {
            reqs.add(Map.of("createShape", Map.of(
                    "objectId",   bodyBoxId,
                    "shapeType",  "TEXT_BOX",
                    "elementProperties", Map.of(
                            "pageObjectId", slideId,
                            "size", Map.of(
                                    "width",  Map.of("magnitude", 6_000_000, "unit", "EMU"),
                                    "height", Map.of("magnitude", 4_000_000, "unit", "EMU")
                            ),
                            "transform", Map.of(
                                    "scaleX", 1, "scaleY", 1,
                                    "translateX", 457_200, "translateY", 1_600_000,
                                    "unit", "EMU"
                            )
                    )
            )));
            reqs.add(Map.of("insertText", Map.of(
                    "objectId", bodyBoxId,
                    "text",     body
            )));
        }

        return reqs;
    }

    // ── Token management ──────────────────────────────────────────────────────

    private String resolveAccessToken(String email) {
        ConnectorToken ct = tokenRepo.findByOwnerEmailAndProvider(email, "google")
                .or(() -> email.isEmpty() ? java.util.Optional.empty()
                        : tokenRepo.findByOwnerEmailAndProvider("", "google"))
                .orElseThrow(() -> new IllegalStateException(
                        "Google account not connected. Visit /mcp to connect."));
        if (isExpiringSoon(ct)) ct = refreshToken(ct);
        return ct.getAccessToken();
    }

    private boolean isExpiringSoon(ConnectorToken ct) {
        return ct.getExpiresAt() != null
                && ct.getExpiresAt().isBefore(LocalDateTime.now().plusSeconds(REFRESH_SECS));
    }

    private ConnectorToken refreshToken(ConnectorToken ct) {
        if (ct.getRefreshToken() == null) return ct;
        log.info("[GoogleSlidesService] Refreshing token for {}", ct.getOwnerEmail());

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id",     props.google().clientId());
        form.add("client_secret", props.google().clientSecret());
        form.add("refresh_token", ct.getRefreshToken());
        form.add("grant_type",    "refresh_token");

        RefreshResponse refreshed = restClientBuilder.build()
                .post().uri(TOKEN_URL)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form).retrieve().body(RefreshResponse.class);

        if (refreshed == null || refreshed.accessToken() == null)
            throw new IllegalStateException("Google token refresh failed");

        ct.setAccessToken(refreshed.accessToken());
        if (refreshed.expiresIn() != null)
            ct.setExpiresAt(LocalDateTime.now().plusSeconds(refreshed.expiresIn()));
        return tokenRepo.save(ct);
    }

    // ── DTOs ─────────────────────────────────────────────────────────────────

    @JsonIgnoreProperties(ignoreUnknown = true)
    record CreatePresentationResponse(
            @JsonProperty("presentationId") String presentationId,
            @JsonProperty("slides")         List<SlideRef> slides
    ) {
        String firstSlideId() {
            return (slides != null && !slides.isEmpty()) ? slides.get(0).objectId() : null;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SlideRef(@JsonProperty("objectId") String objectId) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RefreshResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("expires_in")   Long   expiresIn
    ) {}
}

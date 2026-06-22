package com.agentsystem.connector.service.impl;

import com.agentsystem.connector.service.GoogleCalendarService;

import com.agentsystem.connector.ConnectorProperties;
import com.agentsystem.connector.entity.ConnectorToken;
import com.agentsystem.connector.repository.ConnectorTokenRepository;

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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Google Calendar integration — list events and create new events using the
 * stored Google OAuth token (same token as Docs/Sheets/Slides, provider="google").
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GoogleCalendarServiceImpl implements GoogleCalendarService {

    private static final String CALENDAR_BASE  = "https://www.googleapis.com/calendar/v3/calendars/primary";
    private static final String TOKEN_URL      = "https://oauth2.googleapis.com/token";
    private static final int    REFRESH_SECS   = 300;

    private final ConnectorTokenRepository tokenRepo;
    private final ConnectorProperties      props;
    private final RestClient.Builder       restClientBuilder;

    // ── Public API ────────────────────────────────────────────────────────────

    /** List upcoming events from the user's primary calendar. */
    @Override
    public String listEvents(String ownerEmail, String orgId, int maxResults) {
        String email = ownerEmail != null ? ownerEmail : "";
        String token = resolveAccessToken(email, orgId);

        String timeMin = java.time.Instant.now().toString(); // RFC 3339
        EventListResponse resp = restClientBuilder.build()
                .get()
                .uri(CALENDAR_BASE + "/events?maxResults=" + maxResults
                        + "&singleEvents=true&orderBy=startTime&timeMin=" + timeMin)
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .body(EventListResponse.class);

        if (resp == null || resp.items() == null || resp.items().isEmpty()) {
            return "No upcoming events found in your Google Calendar.";
        }

        String events = resp.items().stream().map(e -> {
            String start = e.start() != null
                    ? (e.start().dateTime() != null ? e.start().dateTime() : e.start().date())
                    : "unknown time";
            return "- " + e.summary() + " at " + start
                    + (e.location() != null ? " (" + e.location() + ")" : "");
        }).collect(Collectors.joining("\n"));

        log.info("[GoogleCalendarService] Listed {} events for {}", resp.items().size(), email);
        return "Upcoming events:\n" + events;
    }

    /** Create a new event on the user's primary calendar. */
    @Override
    public String createEvent(String ownerEmail, String orgId,
                              String title, String startDateTime, String endDateTime,
                              String description, String location) {
        String email = ownerEmail != null ? ownerEmail : "";
        String token = resolveAccessToken(email, orgId);

        Map<String, Object> body = new HashMap<>();
        body.put("summary", title);
        if (description != null && !description.isBlank()) body.put("description", description);
        if (location    != null && !location.isBlank())    body.put("location", location);
        body.put("start", Map.of("dateTime", startDateTime, "timeZone", "UTC"));
        body.put("end",   Map.of("dateTime", endDateTime,   "timeZone", "UTC"));

        EventItem created = restClientBuilder.build()
                .post()
                .uri(CALENDAR_BASE + "/events")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + token)
                .body(body)
                .retrieve()
                .body(EventItem.class);

        String htmlLink = created != null ? created.htmlLink() : null;
        log.info("[GoogleCalendarService] Created event '{}' for {}", title, email);
        return htmlLink != null
                ? "Event created: " + title + ". View it at: " + htmlLink
                : "Event '" + title + "' created successfully.";
    }

    /** Returns true if the user has a valid Google token (shared with Docs/Sheets/Slides). */
    @Override
    public boolean isConnected(String ownerEmail, String orgId) {
        String email = ownerEmail != null ? ownerEmail : "";
        return findToken(email, orgId).isPresent();
    }

    // ── Token management ──────────────────────────────────────────────────────

    private String resolveAccessToken(String email, String orgId) {
        ConnectorToken ct = findToken(email, orgId)
                .orElseThrow(() -> new IllegalStateException(
                        "Google account not connected. Visit /mcp to connect."));
        if (isExpiringSoon(ct)) ct = refreshToken(ct);
        return ct.getAccessToken();
    }

    private java.util.Optional<ConnectorToken> findToken(String email, String orgId) {
        return orgId != null
                ? tokenRepo.findByOwnerEmailAndProviderAndOrgId(email, "google", orgId)
                : tokenRepo.findByOwnerEmailAndProviderAndOrgIdIsNull(email, "google");
    }

    private boolean isExpiringSoon(ConnectorToken ct) {
        return ct.getExpiresAt() != null
                && ct.getExpiresAt().isBefore(LocalDateTime.now().plusSeconds(REFRESH_SECS));
    }

    private ConnectorToken refreshToken(ConnectorToken ct) {
        if (ct.getRefreshToken() == null) return ct;
        log.info("[GoogleCalendarService] Refreshing Google token for {}", ct.getOwnerEmail());
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id",     props.google().clientId());
        form.add("client_secret", props.google().clientSecret());
        form.add("refresh_token", ct.getRefreshToken());
        form.add("grant_type",    "refresh_token");
        try {
            TokenRefreshResponse tr = restClientBuilder.build()
                    .post()
                    .uri(TOKEN_URL)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(TokenRefreshResponse.class);
            if (tr != null && tr.accessToken() != null) {
                ct.setAccessToken(tr.accessToken());
                if (tr.expiresIn() != null) ct.setExpiresAt(LocalDateTime.now().plusSeconds(tr.expiresIn()));
                tokenRepo.save(ct);
            }
        } catch (Exception e) {
            log.warn("[GoogleCalendarService] Token refresh failed: {}", e.getMessage());
        }
        return ct;
    }

    // ── Response DTOs ─────────────────────────────────────────────────────────

    @JsonIgnoreProperties(ignoreUnknown = true)
    record EventListResponse(List<EventItem> items) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record EventItem(
            String summary,
            String location,
            @JsonProperty("htmlLink") String htmlLink,
            EventDateTime start,
            EventDateTime end
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record EventDateTime(
            String dateTime,
            String date
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TokenRefreshResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("expires_in")   Long   expiresIn
    ) {}
}

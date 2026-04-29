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
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Creates and writes to Google Sheets using the stored OAuth token.
 *
 * Flow:
 *  1. Load + refresh token if needed.
 *  2. POST https://sheets.googleapis.com/v4/spreadsheets  → spreadsheetId.
 *  3. PUT  …/values/Sheet1!A1:append  → write rows.
 *  4. Return the spreadsheet URL.
 *
 * Content format accepted: plain text where rows are separated by newlines
 * and columns by tabs or commas.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GoogleSheetsService {

    private static final String SHEETS_BASE  = "https://sheets.googleapis.com/v4/spreadsheets";
    private static final String TOKEN_URL    = "https://oauth2.googleapis.com/token";
    private static final int    REFRESH_SECS = 300;

    private final ConnectorTokenRepository tokenRepo;
    private final ConnectorProperties      props;
    private final RestClient.Builder       restClientBuilder;

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Create a new Google Sheet and populate it with the given data.
     *
     * @param title      spreadsheet title
     * @param content    plain text — rows separated by {@code \n}, columns by tab or comma
     * @param ownerEmail user whose token to use
     * @return the spreadsheet's browser URL
     */
    public String createSpreadsheet(String title, String content, String ownerEmail) {
        String email = ownerEmail != null ? ownerEmail : "";
        String token = resolveAccessToken(email);

        // ── Step 1: create spreadsheet ───────────────────────────────────────
        CreateSheetResponse created = restClientBuilder.build()
                .post()
                .uri(SHEETS_BASE)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of(
                        "properties", Map.of("title", title),
                        "sheets", List.of(Map.of("properties", Map.of("title", "Sheet1")))
                ))
                .retrieve()
                .body(CreateSheetResponse.class);

        if (created == null || created.spreadsheetId() == null) {
            throw new IllegalStateException("Sheets API returned no spreadsheetId");
        }
        String sheetId = created.spreadsheetId();
        log.info("[GoogleSheetsService] Created sheet {} for {}", sheetId, email);

        // ── Step 2: write rows ────────────────────────────────────────────────
        if (content != null && !content.isBlank()) {
            List<List<Object>> rows = parseRows(content);
            Map<String, Object> body = Map.of(
                    "range",          "Sheet1!A1",
                    "majorDimension", "ROWS",
                    "values",         rows
            );
            restClientBuilder.build()
                    .put()
                    .uri(SHEETS_BASE + "/" + sheetId + "/values/Sheet1!A1?valueInputOption=RAW")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + token)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        }

        String url = "https://docs.google.com/spreadsheets/d/" + sheetId + "/edit";
        log.info("[GoogleSheetsService] Spreadsheet ready: {}", url);
        return url;
    }

    public boolean isConnected(String ownerEmail) {
        String email = ownerEmail != null ? ownerEmail : "";
        return tokenRepo.findByOwnerEmailAndProvider(email, "google").isPresent();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Split text into a 2-D list: rows by newline, cells by tab then comma. */
    private List<List<Object>> parseRows(String content) {
        List<List<Object>> rows = new ArrayList<>();
        for (String line : content.split("\n")) {
            if (line.isBlank()) continue;
            String[] cells = line.contains("\t") ? line.split("\t") : line.split(",");
            rows.add(new ArrayList<>(Arrays.asList((Object[]) cells)));
        }
        return rows.isEmpty() ? List.of(List.of(content)) : rows;
    }

    // ── Token management (mirrors GoogleDocsService) ──────────────────────────

    private String resolveAccessToken(String email) {
        ConnectorToken ct = tokenRepo.findByOwnerEmailAndProvider(email, "google")
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
        log.info("[GoogleSheetsService] Refreshing token for {}", ct.getOwnerEmail());

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

    @JsonIgnoreProperties(ignoreUnknown = true)
    record CreateSheetResponse(@JsonProperty("spreadsheetId") String spreadsheetId) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RefreshResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("expires_in")   Long   expiresIn
    ) {}
}

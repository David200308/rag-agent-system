package com.agentsystem.connector.service.impl;

import com.agentsystem.connector.service.ConnectorService;
import com.agentsystem.connector.service.TelegramService;

import com.agentsystem.connector.ConnectorProperties;
import com.agentsystem.connector.entity.ConnectorOAuthState;
import com.agentsystem.connector.entity.ConnectorToken;
import com.agentsystem.connector.repository.ConnectorOAuthStateRepository;
import com.agentsystem.connector.repository.ConnectorTokenRepository;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConnectorServiceImpl implements ConnectorService {

    private static final String GOOGLE_AUTH_URL  = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String GOOGLE_TOKEN_URL  = "https://oauth2.googleapis.com/token";
    private static final String FIGMA_AUTH_URL    = "https://www.figma.com/oauth";
    private static final String FIGMA_TOKEN_URL   = "https://api.figma.com/v1/oauth/token";

    private static final List<String> GOOGLE_SCOPES = List.of(
            "openid",
            "https://www.googleapis.com/auth/userinfo.email",
            "https://www.googleapis.com/auth/documents",
            "https://www.googleapis.com/auth/spreadsheets",
            "https://www.googleapis.com/auth/presentations",
            "https://www.googleapis.com/auth/drive.file",
            "https://www.googleapis.com/auth/calendar"
    );

    private final ConnectorProperties          props;
    private final ConnectorTokenRepository     tokenRepo;
    private final ConnectorOAuthStateRepository stateRepo;
    private final RestClient.Builder           restClientBuilder;
    private final TelegramService              telegramService;

    // ── Auth URL generation ───────────────────────────────────────────────────

    @Override
    public String getAuthUrl(String provider, String ownerUuid, String orgId) {
        validateProvider(provider);

        // Telegram uses the Login Widget, not an OAuth redirect
        if ("telegram".equals(provider)) {
            throw new IllegalArgumentException("Telegram does not use OAuth — use the Login Widget");
        }

        String state       = UUID.randomUUID().toString().replace("-", "");
        String callbackUrl = callbackUrl(provider);

        stateRepo.save(ConnectorOAuthState.builder()
                .state(state)
                .ownerUuid(ownerUuid)
                .provider(provider)
                .orgId(orgId)
                .expiresAt(LocalDateTime.now().plusMinutes(10))
                .build());

        return switch (provider) {
            case "google" -> UriComponentsBuilder.fromHttpUrl(GOOGLE_AUTH_URL)
                    .queryParam("client_id",     props.google().clientId())
                    .queryParam("redirect_uri",  callbackUrl)
                    .queryParam("response_type", "code")
                    .queryParam("scope",         String.join(" ", GOOGLE_SCOPES))
                    .queryParam("access_type",   "offline")
                    .queryParam("prompt",        "consent")
                    .queryParam("state",         state)
                    .toUriString();

            case "figma" -> UriComponentsBuilder.fromHttpUrl(FIGMA_AUTH_URL)
                    .queryParam("client_id",     props.figma().clientId())
                    .queryParam("redirect_uri",  callbackUrl)
                    .queryParam("response_type", "code")
                    .queryParam("scope",         "file_read")
                    .queryParam("state",         state)
                    .toUriString();

            default -> throw new IllegalArgumentException("Unknown provider: " + provider);
        };
    }

    // ── Code exchange ─────────────────────────────────────────────────────────

    @Override
    public void exchangeCode(String provider, String code, String state) {
        validateProvider(provider);

        ConnectorOAuthState oauthState = stateRepo.findByState(state)
                .orElseThrow(() -> new IllegalArgumentException("Invalid OAuth state"));

        if (oauthState.getExpiresAt().isBefore(LocalDateTime.now())) {
            stateRepo.delete(oauthState);
            throw new IllegalArgumentException("OAuth state expired");
        }
        if (!oauthState.getProvider().equals(provider)) {
            throw new IllegalArgumentException("Provider mismatch in OAuth state");
        }

        String ownerUuid = oauthState.getOwnerUuid() != null ? oauthState.getOwnerUuid() : "";
        String orgId      = oauthState.getOrgId();
        stateRepo.delete(oauthState);

        TokenResponse tr = switch (provider) {
            case "google" -> exchangeGoogle(code);
            case "figma"  -> exchangeFigma(code);
            default -> throw new IllegalArgumentException("Unknown provider: " + provider);
        };

        ConnectorToken token = findToken(ownerUuid, provider, orgId)
                .orElse(ConnectorToken.builder()
                        .ownerUuid(ownerUuid)
                        .provider(provider)
                        .orgId(orgId)
                        .build());

        token.setAccessToken(tr.accessToken());
        if (tr.refreshToken() != null) token.setRefreshToken(tr.refreshToken());
        token.setTokenType(tr.tokenType() != null ? tr.tokenType() : "Bearer");
        token.setScope(tr.scope());
        token.setExpiresAt(tr.expiresIn() != null
                ? LocalDateTime.now().plusSeconds(tr.expiresIn()) : null);

        // Migrate any anonymous personal token stored before auth was properly wired
        if (!ownerUuid.isEmpty() && orgId == null) {
            tokenRepo.findByOwnerUuidAndProviderAndOrgIdIsNull("", provider).ifPresent(tokenRepo::delete);
        }

        tokenRepo.save(token);
        log.info("[ConnectorService] Stored {} token for {} orgId={}", provider, ownerUuid, orgId);
    }

    // ── Status & disconnect ───────────────────────────────────────────────────

    @Override
    public Map<String, Boolean> getStatus(String ownerUuid, String orgId) {
        String uuid = ownerUuid != null ? ownerUuid : "";
        Set<String> connected = (orgId != null
                ? tokenRepo.findByOwnerUuidAndOrgId(uuid, orgId)
                : tokenRepo.findByOwnerUuidAndOrgIdIsNull(uuid))
                .stream().map(ConnectorToken::getProvider).collect(Collectors.toSet());

        Map<String, Boolean> status = new LinkedHashMap<>();
        status.put("google",   connected.contains("google"));
        status.put("figma",    connected.contains("figma"));
        status.put("telegram", telegramService.isConnected(uuid, orgId));
        return status;
    }

    @Override
    @Transactional
    public void disconnect(String provider, String ownerUuid, String orgId) {
        validateProvider(provider);
        String uuid = ownerUuid != null ? ownerUuid : "";
        if ("telegram".equals(provider)) {
            telegramService.disconnect(uuid, orgId);
            return;
        }
        if (orgId != null) {
            tokenRepo.deleteByOwnerUuidAndProviderAndOrgId(uuid, provider, orgId);
        } else {
            tokenRepo.deleteByOwnerUuidAndProviderAndOrgIdIsNull(uuid, provider);
            // Also remove any anonymous personal token stored before auth was properly wired
            if (!uuid.isEmpty()) {
                tokenRepo.deleteByOwnerUuidAndProviderAndOrgIdIsNull("", provider);
            }
        }
        log.info("[ConnectorService] Disconnected {} for {} orgId={}", provider, uuid, orgId);
    }

    // ── Token access (for downstream API calls) ───────────────────────────────

    @Override
    public Optional<ConnectorToken> getToken(String provider, String ownerUuid, String orgId) {
        String uuid = ownerUuid != null ? ownerUuid : "";
        return findToken(uuid, provider, orgId);
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private TokenResponse exchangeGoogle(String code) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("code",          code);
        form.add("client_id",     props.google().clientId());
        form.add("client_secret", props.google().clientSecret());
        form.add("redirect_uri",  callbackUrl("google"));
        form.add("grant_type",    "authorization_code");
        return postForm(GOOGLE_TOKEN_URL, form, null);
    }

    private TokenResponse exchangeFigma(String code) {
        // Figma uses HTTP Basic auth (clientId:clientSecret) for token exchange
        String credentials = Base64.getEncoder().encodeToString(
                (props.figma().clientId() + ":" + props.figma().clientSecret()).getBytes());

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("code",         code);
        form.add("redirect_uri", callbackUrl("figma"));
        form.add("grant_type",   "authorization_code");
        return postForm(FIGMA_TOKEN_URL, form, "Basic " + credentials);
    }

    private TokenResponse postForm(String url, MultiValueMap<String, String> form, String authHeader) {
        RestClient client = restClientBuilder.build();
        var spec = client.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form);
        if (authHeader != null) spec = spec.header("Authorization", authHeader);
        return spec.retrieve().body(TokenResponse.class);
    }

    private String callbackUrl(String provider) {
        return props.callbackBaseUrl() + "/api/connectors/" + provider + "/callback";
    }

    private void validateProvider(String provider) {
        if (!Set.of("google", "figma", "telegram").contains(provider)) {
            throw new IllegalArgumentException("Unknown provider: " + provider);
        }
    }

    @Scheduled(fixedDelay = 3_600_000)
    void purgeExpiredStates() {
        stateRepo.deleteExpired(LocalDateTime.now());
    }

    private Optional<ConnectorToken> findToken(String ownerUuid, String provider, String orgId) {
        return orgId != null
                ? tokenRepo.findByOwnerUuidAndProviderAndOrgId(ownerUuid, provider, orgId)
                : tokenRepo.findByOwnerUuidAndProviderAndOrgIdIsNull(ownerUuid, provider);
    }

    // ── Inner DTO ─────────────────────────────────────────────────────────────

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TokenResponse(
            @JsonProperty("access_token")  String accessToken,
            @JsonProperty("refresh_token") String refreshToken,
            @JsonProperty("token_type")    String tokenType,
            @JsonProperty("scope")         String scope,
            @JsonProperty("expires_in")    Long   expiresIn
    ) {}
}

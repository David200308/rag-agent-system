package com.agentsystem.auth.service.impl;

import com.agentsystem.auth.service.PasskeyService;
import com.agentsystem.auth.service.JwtService;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.agentsystem.auth.PasskeyProperties;
import com.agentsystem.auth.entity.PasskeyCredential;
import com.agentsystem.auth.repository.PasskeyCredentialRepository;
import com.agentsystem.user.entity.User;
import com.agentsystem.user.service.UserAccountService;
import com.yubico.webauthn.*;
import com.yubico.webauthn.data.*;
import com.yubico.webauthn.data.exception.Base64UrlException;
import com.yubico.webauthn.exception.AssertionFailedException;
import com.yubico.webauthn.exception.RegistrationFailedException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Registration/authentication challenges are short-lived (5 min) and stored in Redis
 * under {@code passkey-challenge:<type>:<email>} with that as the key's native TTL —
 * no separate cleanup job needed. {@code type} is REGISTER or AUTHENTICATE; a fresh
 * challenge overwrites any previous one for the same (email, type) pair.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PasskeyServiceImpl implements PasskeyService, CredentialRepository {

    private final PasskeyProperties           props;
    private final PasskeyCredentialRepository credRepo;
    private final StringRedisTemplate         redisTemplate;
    private final JwtService                  jwtService;
    private final UserAccountService          userAccountService;

    private RelyingParty relyingParty;

    private static final String  CHALLENGE_KEY_PREFIX = "passkey-challenge:";
    private static final Duration CHALLENGE_TTL        = Duration.ofSeconds(300);

    /** Challenge payload persisted to Redis: the WebAuthn request JSON plus the login mode/org captured at begin. */
    private record ChallengeData(String requestJson, String mode, String orgId) {}

    private final ObjectMapper passkeyMapper = new ObjectMapper()
            .registerModule(new Jdk8Module())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final SecureRandom random = new SecureRandom();

    @PostConstruct
    void init() {
        relyingParty = RelyingParty.builder()
                .identity(RelyingPartyIdentity.builder()
                        .id(props.rpId())
                        .name(props.rpName())
                        .build())
                .credentialRepository(this)
                .origins(Set.of(props.origin()))
                .build();
    }

    // ── CredentialRepository ─────────────────────────────────────────────────────

    @Override
    public Set<PublicKeyCredentialDescriptor> getCredentialIdsForUsername(String email) {
        return credRepo.findByEmail(email).stream()
                .map(c -> PublicKeyCredentialDescriptor.builder()
                        .id(b64(c.getCredentialId()))
                        .type(PublicKeyCredentialType.PUBLIC_KEY)
                        .build())
                .collect(Collectors.toSet());
    }

    @Override
    public Optional<ByteArray> getUserHandleForUsername(String email) {
        return credRepo.findByEmail(email).stream()
                .findFirst()
                .map(c -> b64(c.getUserHandle()));
    }

    @Override
    public Optional<String> getUsernameForUserHandle(ByteArray userHandle) {
        return credRepo.findByUserHandle(userHandle.getBase64Url())
                .map(PasskeyCredential::getEmail);
    }

    @Override
    public Optional<RegisteredCredential> lookup(ByteArray credentialId, ByteArray userHandle) {
        return credRepo.findByCredentialId(credentialId.getBase64Url())
                .filter(c -> c.getUserHandle().equals(userHandle.getBase64Url()))
                .map(this::toRegisteredCredential);
    }

    @Override
    public Set<RegisteredCredential> lookupAll(ByteArray credentialId) {
        return credRepo.findByCredentialId(credentialId.getBase64Url())
                .map(this::toRegisteredCredential)
                .map(Set::of)
                .orElseGet(Set::of);
    }

    private RegisteredCredential toRegisteredCredential(PasskeyCredential c) {
        return RegisteredCredential.builder()
                .credentialId(b64(c.getCredentialId()))
                .userHandle(b64(c.getUserHandle()))
                .publicKeyCose(b64(c.getPublicKeyCose()))
                .signatureCount(c.getSignCount())
                .build();
    }

    /** Wraps the checked Base64UrlException so lambdas stay clean. */
    private static ByteArray b64(String s) {
        try {
            return ByteArray.fromBase64Url(s);
        } catch (Base64UrlException e) {
            throw new IllegalStateException("Stored credential contains invalid base64url data", e);
        }
    }

    // ── Registration ─────────────────────────────────────────────────────────────

    @Transactional
    @Override
    public String startRegistration(String email) throws Exception {
        String userHandle = credRepo.findByEmail(email).stream()
                .findFirst()
                .map(PasskeyCredential::getUserHandle)
                .orElseGet(this::generateUserHandle);

        UserIdentity user = UserIdentity.builder()
                .name(email)
                .displayName(email)
                .id(b64(userHandle))
                .build();

        PublicKeyCredentialCreationOptions options = relyingParty.startRegistration(
                StartRegistrationOptions.builder()
                        .user(user)
                        .authenticatorSelection(AuthenticatorSelectionCriteria.builder()
                                .residentKey(ResidentKeyRequirement.PREFERRED)
                                .userVerification(UserVerificationRequirement.PREFERRED)
                                .build())
                        .build());

        // Store the full JSON (nulls intact) for round-trip deserialization on finish.
        String storageJson = options.toJson();

        redisTemplate.opsForValue().set(
                challengeKey(email, "REGISTER"),
                passkeyMapper.writeValueAsString(new ChallengeData(storageJson, "PERSONAL", null)),
                CHALLENGE_TTL);

        log.info("[PasskeyService] Registration challenge issued for {}", email);
        return stripNulls(storageJson);
    }

    @Transactional
    @Override
    public void finishRegistration(String email, String responseJson)
            throws Exception {
        ChallengeData challenge = getChallenge(email, "REGISTER")
                .orElseThrow(() -> new IllegalStateException("No pending registration or challenge expired"));

        PublicKeyCredentialCreationOptions options =
                passkeyMapper.readValue(challenge.requestJson(), PublicKeyCredentialCreationOptions.class);

        RegistrationResult result;
        try {
            result = relyingParty.finishRegistration(
                    FinishRegistrationOptions.builder()
                            .request(options)
                            .response(PublicKeyCredential.parseRegistrationResponseJson(responseJson))
                            .build());
        } catch (RegistrationFailedException e) {
            throw new IllegalArgumentException("Passkey registration failed: " + e.getMessage(), e);
        }

        String credentialId = result.getKeyId().getId().getBase64Url();
        String publicKeyCose = result.getPublicKeyCose().getBase64Url();
        String userHandle = options.getUser().getId().getBase64Url();

        PasskeyCredential cred = credRepo.findByCredentialId(credentialId)
                .orElseGet(PasskeyCredential::new);
        cred.setEmail(email);
        cred.setCredentialId(credentialId);
        cred.setPublicKeyCose(publicKeyCose);
        cred.setSignCount(result.getSignatureCount());
        cred.setUserHandle(userHandle);
        credRepo.save(cred);

        redisTemplate.delete(challengeKey(email, "REGISTER"));
        log.info("[PasskeyService] Passkey registered for {}", email);
    }

    // ── Authentication ────────────────────────────────────────────────────────────

    @Transactional
    @Override
    public String startAuthentication(String email, String mode, String orgId) throws Exception {
        if (!credRepo.existsByEmail(email)) {
            throw new IllegalArgumentException("No passkey registered for this email");
        }

        AssertionRequest assertionRequest = relyingParty.startAssertion(
                StartAssertionOptions.builder()
                        .username(email)
                        .userVerification(UserVerificationRequirement.PREFERRED)
                        .build());

        String fullJson = assertionRequest.toJson();

        redisTemplate.opsForValue().set(
                challengeKey(email, "AUTHENTICATE"),
                passkeyMapper.writeValueAsString(new ChallengeData(fullJson, mode != null ? mode : "PERSONAL", orgId)),
                CHALLENGE_TTL);

        log.info("[PasskeyService] Authentication challenge issued for {}", email);
        // Use toJson() for fidelity, then strip nulls — the yubico library's custom serializers
        // bypass an external mapper's NON_NULL setting, so browserMapper.writeValueAsString()
        // was leaving `"transports": null` in place, which the WebAuthn API rejects.
        return stripNulls(passkeyMapper.writeValueAsString(assertionRequest.getPublicKeyCredentialRequestOptions()));
    }

    @Transactional
    @Override
    public String finishAuthentication(String email, String responseJson) throws Exception {
        ChallengeData challenge = getChallenge(email, "AUTHENTICATE")
                .orElseThrow(() -> new IllegalStateException("No pending authentication or challenge expired"));

        AssertionRequest assertionRequest =
                passkeyMapper.readValue(challenge.requestJson(), AssertionRequest.class);

        AssertionResult result;
        try {
            result = relyingParty.finishAssertion(
                    FinishAssertionOptions.builder()
                            .request(assertionRequest)
                            .response(PublicKeyCredential.parseAssertionResponseJson(responseJson))
                            .build());
        } catch (AssertionFailedException e) {
            throw new IllegalArgumentException("Passkey authentication failed: " + e.getMessage(), e);
        }

        // Update stored sign count to prevent replay attacks
        credRepo.findByCredentialId(result.getCredential().getCredentialId().getBase64Url())
                .ifPresent(c -> {
                    c.setSignCount(result.getSignatureCount());
                    credRepo.save(c);
                });

        redisTemplate.delete(challengeKey(email, "AUTHENTICATE"));

        // Passkey login bypassed the whitelist entirely before this change — apply the
        // same status=USER/enabled gate as OTP login so PRE_USER can't use a registered
        // passkey as a backdoor around approval.
        User user = userAccountService.findActiveUser(email)
                .orElseThrow(() -> new IllegalArgumentException(
                        "This account is not authorised to access the system."));

        String resolvedMode = challenge.mode() != null ? challenge.mode() : "PERSONAL";
        String jwt = jwtService.generate(user.getUuid(), resolvedMode, challenge.orgId());
        log.info("[PasskeyService] Passkey authentication successful for {} mode={}", email, resolvedMode);
        return jwt;
    }

    // ── Status & Management ──────────────────────────────────────────────────────

    @Override
    public boolean hasPasskey(String email) {
        return credRepo.existsByEmail(email);
    }

    @Transactional
    @Override
    public void deletePasskeys(String email) {
        credRepo.deleteByEmail(email);
        redisTemplate.delete(challengeKey(email, "REGISTER"));
        redisTemplate.delete(challengeKey(email, "AUTHENTICATE"));
        log.info("[PasskeyService] Passkeys deleted for {}", email);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────

    private String challengeKey(String email, String type) {
        return CHALLENGE_KEY_PREFIX + type + ":" + email;
    }

    private Optional<ChallengeData> getChallenge(String email, String type) throws Exception {
        String raw = redisTemplate.opsForValue().get(challengeKey(email, type));
        if (raw == null) {
            return Optional.empty();
        }
        return Optional.of(passkeyMapper.readValue(raw, ChallengeData.class));
    }

    private String generateUserHandle() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return new ByteArray(bytes).getBase64Url();
    }

    /**
     * Parses a JSON string and removes all null-valued fields at every depth.
     * The yubico library's classes use @JsonValue / toJson() internally, so they
     * bypass an external ObjectMapper's NON_NULL include setting. Post-processing
     * the raw toJson() output is the only reliable way to keep null fields out of
     * the response sent to the browser WebAuthn API.
     */
    private String stripNulls(String json) throws Exception {
        JsonNode root = passkeyMapper.readTree(json);
        stripNullsNode(root);
        return passkeyMapper.writeValueAsString(root);
    }

    private static void stripNullsNode(JsonNode node) {
        if (node.isObject()) {
            ObjectNode obj = (ObjectNode) node;
            java.util.List<String> nullKeys = new java.util.ArrayList<>();
            obj.fields().forEachRemaining(e -> {
                if (e.getValue().isNull()) nullKeys.add(e.getKey());
                else stripNullsNode(e.getValue());
            });
            nullKeys.forEach(obj::remove);
        } else if (node.isArray()) {
            node.forEach(PasskeyServiceImpl::stripNullsNode);
        }
    }
}

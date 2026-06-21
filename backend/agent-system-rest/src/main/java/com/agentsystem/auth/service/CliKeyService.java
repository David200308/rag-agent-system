package com.agentsystem.auth.service;

import com.agentsystem.auth.entity.CliPublicKey;
import com.agentsystem.auth.repository.CliPublicKeyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;

@Slf4j
@Service
@RequiredArgsConstructor
public class CliKeyService {

    // Replay-attack window: reject signatures older than 5 minutes
    private static final long MAX_CLOCK_SKEW_SECONDS = 300;

    // DER SubjectPublicKeyInfo prefix for Ed25519 (OID 1.3.101.112)
    // Prepended to the raw 32-byte key so Java's X509EncodedKeySpec can parse it
    private static final byte[] ED25519_X509_PREFIX = {
        0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00
    };

    private final CliPublicKeyRepository repo;

    // ── Registration ──────────────────────────────────────────────────────────

    @Transactional
    public String registerKey(String email, String publicKeyBase64) {
        byte[] raw = Base64.getDecoder().decode(publicKeyBase64);
        if (raw.length != 32) {
            throw new IllegalArgumentException("Invalid Ed25519 public key: expected 32 bytes");
        }

        CliPublicKey record = repo.findByUserEmail(email)
                .orElseGet(() -> new CliPublicKey());
        record.setUserEmail(email);
        record.setPublicKeyBase64(publicKeyBase64);
        record.setFingerprint(publicKeyBase64.substring(0, 8));
        if (record.getRegisteredAt() == null) {
            record.setRegisteredAt(Instant.now());
        }
        repo.save(record);

        log.info("[CliKeyService] Public key registered for {}, fingerprint={}", email, record.getFingerprint());
        return record.getFingerprint();
    }

    // ── Signature verification ────────────────────────────────────────────────

    /**
     * Verifies an X-Cli-Signature header value.
     *
     * Canonical message signed by the CLI:
     *   "{cliVersion} {METHOD} {/api/path} {email} {unixTimestamp}"
     *
     * @return true if signature is valid and timestamp is fresh
     */
    public boolean verify(String email,
                          String signatureBase64,
                          String cliVersion,
                          String method,
                          String path,
                          long   timestamp) {
        // 1. Reject stale / future timestamps (replay protection)
        long now = Instant.now().getEpochSecond();
        if (Math.abs(now - timestamp) > MAX_CLOCK_SKEW_SECONDS) {
            log.warn("[CliKeyService] Rejected stale signature for {} (skew={}s)", email, now - timestamp);
            return false;
        }

        // 2. Look up stored public key
        CliPublicKey stored = repo.findByUserEmail(email).orElse(null);
        if (stored == null) {
            log.warn("[CliKeyService] No CLI key registered for {}", email);
            return false;
        }

        // 3. Reconstruct the canonical message
        String message = cliVersion + " " + method + " " + path + " " + email + " " + timestamp;

        // 4. Verify Ed25519 signature
        try {
            byte[]    rawKey  = Base64.getDecoder().decode(stored.getPublicKeyBase64());
            byte[]    derKey  = wrapWithPrefix(rawKey);
            PublicKey pubKey  = KeyFactory.getInstance("Ed25519")
                    .generatePublic(new X509EncodedKeySpec(derKey));

            Signature sig = Signature.getInstance("Ed25519");
            sig.initVerify(pubKey);
            sig.update(message.getBytes(StandardCharsets.UTF_8));

            boolean valid = sig.verify(Base64.getDecoder().decode(signatureBase64));
            if (valid) {
                repo.findByUserEmail(email).ifPresent(k -> {
                    k.setLastSeenAt(Instant.now());
                    repo.save(k);
                });
            } else {
                log.warn("[CliKeyService] Bad signature from {}", email);
            }
            return valid;

        } catch (Exception e) {
            log.error("[CliKeyService] Signature verification error for {}: {}", email, e.getMessage());
            return false;
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static byte[] wrapWithPrefix(byte[] rawKey) {
        byte[] out = new byte[ED25519_X509_PREFIX.length + rawKey.length];
        System.arraycopy(ED25519_X509_PREFIX, 0, out, 0, ED25519_X509_PREFIX.length);
        System.arraycopy(rawKey, 0, out, ED25519_X509_PREFIX.length, rawKey.length);
        return out;
    }
}

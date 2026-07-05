package com.agentsystem.auth.service;

import com.agentsystem.auth.service.impl.CliKeyServiceImpl;

import com.agentsystem.auth.entity.CliPublicKey;
import com.agentsystem.auth.repository.CliPublicKeyRepository;
import com.agentsystem.user.entity.User;
import com.agentsystem.user.entity.UserStatus;
import com.agentsystem.user.service.UserAccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CliKeyServiceTest {

    @Mock CliPublicKeyRepository repo;
    @Mock UserAccountService     userAccountService;

    CliKeyService service;

    @BeforeEach
    void setUp() {
        service = new CliKeyServiceImpl(repo, userAccountService);
    }

    /** resolveUuid() bridges email -> uuid; reuse the email string as the uuid for test simplicity. */
    private void stubUuid(String email) {
        lenient().when(userAccountService.findByEmail(email))
                .thenReturn(Optional.of(new User(email, email, UserStatus.USER, true)));
    }

    // ── registerKey ───────────────────────────────────────────────────────────

    @Test
    void registerKey_validKey_savesAndReturnsFingerprint() {
        stubUuid("user@test.com");
        byte[] raw32 = new byte[32];
        raw32[0] = (byte) 0xAB;
        String key64 = Base64.getEncoder().encodeToString(raw32);

        when(repo.findByUserUuid("user@test.com")).thenReturn(Optional.empty());
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));

        String fp = service.registerKey("user@test.com", key64);

        assertThat(fp).isNotBlank();
        assertThat(fp.length()).isEqualTo(8);
        verify(repo).save(any(CliPublicKey.class));
    }

    @Test
    void registerKey_invalidKeyLength_throwsIllegalArgument() {
        byte[] wrong = new byte[16]; // not 32 bytes
        String bad64 = Base64.getEncoder().encodeToString(wrong);

        assertThatThrownBy(() -> service.registerKey("user@test.com", bad64))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("32 bytes");
    }

    @Test
    void registerKey_unregisteredEmail_throwsIllegalArgument() {
        byte[] raw32 = new byte[32];
        String key64 = Base64.getEncoder().encodeToString(raw32);

        assertThatThrownBy(() -> service.registerKey("ghost@test.com", key64))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not a registered user");
    }

    @Test
    void registerKey_existingKey_updatesIt() {
        stubUuid("user@test.com");
        byte[] raw32 = new byte[32];
        String key64 = Base64.getEncoder().encodeToString(raw32);

        CliPublicKey existing = new CliPublicKey("user@test.com", key64);
        existing.setRegisteredAt(Instant.now().minusSeconds(3600));

        when(repo.findByUserUuid("user@test.com")).thenReturn(Optional.of(existing));
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));

        String fp = service.registerKey("user@test.com", key64);

        assertThat(fp).isNotBlank();
        // Existing record updated — save called once
        ArgumentCaptor<CliPublicKey> captor = ArgumentCaptor.forClass(CliPublicKey.class);
        verify(repo).save(captor.capture());
        assertThat(captor.getValue().getPublicKeyBase64()).isEqualTo(key64);
    }

    // ── verify — stale timestamp ──────────────────────────────────────────────

    @Test
    void verify_staleTimestamp_returnsFalse() {
        long staleTs = Instant.now().getEpochSecond() - 600; // 10 min ago

        boolean result = service.verify("user@test.com", "sig", "1.0.0", "GET", "/api/test", staleTs);

        assertThat(result).isFalse();
        verify(repo, never()).findByUserUuid(any());
    }

    @Test
    void verify_futureTimestamp_returnsFalse() {
        long futureTs = Instant.now().getEpochSecond() + 600; // 10 min in future

        boolean result = service.verify("user@test.com", "sig", "1.0.0", "GET", "/api/test", futureTs);

        assertThat(result).isFalse();
    }

    // ── verify — no key registered ────────────────────────────────────────────

    @Test
    void verify_noKeyRegistered_returnsFalse() {
        stubUuid("user@test.com");
        long now = Instant.now().getEpochSecond();
        when(repo.findByUserUuid("user@test.com")).thenReturn(Optional.empty());

        boolean result = service.verify("user@test.com", "sig", "1.0.0", "GET", "/api/test", now);

        assertThat(result).isFalse();
    }

    @Test
    void verify_unregisteredEmail_returnsFalse() {
        long now = Instant.now().getEpochSecond();

        boolean result = service.verify("ghost@test.com", "sig", "1.0.0", "GET", "/api/test", now);

        assertThat(result).isFalse();
        verify(repo, never()).findByUserUuid(any());
    }

    // ── verify — valid Ed25519 signature ─────────────────────────────────────

    @Test
    void verify_validEd25519Signature_returnsTrue() throws Exception {
        stubUuid("user@test.com");
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
        KeyPair kp = kpg.generateKeyPair();

        // Raw Ed25519 public key is 32 bytes in X.509 format minus the prefix
        // In Java, getEncoded() returns the X.509 DER format (44 bytes total with prefix)
        // The service prepends the 12-byte X.509 prefix to a raw 32-byte key.
        // So we need to strip the prefix from the encoded key to get the raw 32 bytes.
        byte[] encoded = kp.getPublic().getEncoded(); // 44 bytes (12 prefix + 32 raw)
        byte[] rawKey  = new byte[32];
        System.arraycopy(encoded, 12, rawKey, 0, 32);
        String pubKey64 = Base64.getEncoder().encodeToString(rawKey);

        long now = Instant.now().getEpochSecond();
        String message = "1.0.0 GET /api/test user@test.com " + now;

        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(kp.getPrivate());
        signer.update(message.getBytes());
        String sig64 = Base64.getEncoder().encodeToString(signer.sign());

        CliPublicKey record = new CliPublicKey("user@test.com", pubKey64);
        when(repo.findByUserUuid("user@test.com")).thenReturn(Optional.of(record));
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));

        boolean result = service.verify("user@test.com", sig64, "1.0.0", "GET", "/api/test", now);

        assertThat(result).isTrue();
    }

    @Test
    void verify_wrongSignature_returnsFalse() throws Exception {
        stubUuid("user@test.com");
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
        KeyPair kp = kpg.generateKeyPair();

        byte[] encoded = kp.getPublic().getEncoded();
        byte[] rawKey  = new byte[32];
        System.arraycopy(encoded, 12, rawKey, 0, 32);
        String pubKey64 = Base64.getEncoder().encodeToString(rawKey);

        long now = Instant.now().getEpochSecond();

        // Sign with a different key
        KeyPair wrongKp = kpg.generateKeyPair();
        String message = "1.0.0 GET /api/test user@test.com " + now;
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(wrongKp.getPrivate());
        signer.update(message.getBytes());
        String wrongSig = Base64.getEncoder().encodeToString(signer.sign());

        CliPublicKey record = new CliPublicKey("user@test.com", pubKey64);
        when(repo.findByUserUuid("user@test.com")).thenReturn(Optional.of(record));

        boolean result = service.verify("user@test.com", wrongSig, "1.0.0", "GET", "/api/test", now);

        assertThat(result).isFalse();
    }

    @Test
    void verify_invalidBase64Signature_returnsFalse() {
        stubUuid("user@test.com");
        long now = Instant.now().getEpochSecond();

        byte[] raw32 = new byte[32];
        String pubKey64 = Base64.getEncoder().encodeToString(raw32);
        CliPublicKey record = new CliPublicKey("user@test.com", pubKey64);
        when(repo.findByUserUuid("user@test.com")).thenReturn(Optional.of(record));

        boolean result = service.verify("user@test.com", "not!!valid!!base64", "1.0.0", "GET", "/api/test", now);

        assertThat(result).isFalse();
    }
}

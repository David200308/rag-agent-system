package com.agentsystem.user.crypto;

import com.agentsystem.auth.AuthProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Base64;

/**
 * Deterministic AES-256-GCM encryption for the {@code users.email} column.
 *
 * The GCM nonce is derived from HMAC-SHA256(nonceKey, plaintext) instead of being random, so
 * the same email always encrypts to the same ciphertext — required so JPA derived queries
 * ({@code findByEmail}) can do an equality match directly against the encrypted column.
 * This necessarily leaks whether two rows share the same email (inherent to needing a
 * lookup by email at all), but not the plaintext itself without the key.
 */
@Component
public class EmailEncryptor {

    private static final String AES     = "AES";
    private static final String AES_GCM = "AES/GCM/NoPadding";
    private static final String HMAC    = "HmacSHA256";
    private static final int GCM_TAG_BITS = 128;
    private static final int NONCE_BYTES  = 12;

    private final SecretKeySpec encKey;
    private final SecretKeySpec nonceKey;

    /**
     * @throws IllegalStateException if auth.email-encryption-key doesn't decode to at
     *         least 256 bits — fails app startup rather than silently using a weak key.
     */
    public EmailEncryptor(AuthProperties props) {
        byte[] masterKey = Base64.getDecoder().decode(props.emailEncryptionKey());
        if (masterKey.length < 32) {
            throw new IllegalStateException(
                    "auth.email-encryption-key must decode to at least 32 bytes (256 bits)");
        }
        // Derive independent subkeys for encryption vs. nonce-derivation from the one
        // configured master key, rather than reusing it directly for both purposes.
        this.encKey   = new SecretKeySpec(hmac(masterKey, "email-enc".getBytes(StandardCharsets.UTF_8)), AES);
        this.nonceKey = new SecretKeySpec(hmac(masterKey, "email-nonce".getBytes(StandardCharsets.UTF_8)), HMAC);
    }

    public String encrypt(String plaintext) {
        try {
            String normalised = normalise(plaintext);
            byte[] nonce = Arrays.copyOf(
                    hmac(nonceKey.getEncoded(), normalised.getBytes(StandardCharsets.UTF_8)), NONCE_BYTES);
            Cipher cipher = Cipher.getInstance(AES_GCM);
            cipher.init(Cipher.ENCRYPT_MODE, encKey, new GCMParameterSpec(GCM_TAG_BITS, nonce));
            byte[] ciphertext = cipher.doFinal(normalised.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[nonce.length + ciphertext.length];
            System.arraycopy(nonce, 0, combined, 0, nonce.length);
            System.arraycopy(ciphertext, 0, combined, nonce.length, ciphertext.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to encrypt email", e);
        }
    }

    public String decrypt(String encoded) {
        try {
            byte[] combined   = Base64.getDecoder().decode(encoded);
            byte[] nonce      = Arrays.copyOfRange(combined, 0, NONCE_BYTES);
            byte[] ciphertext = Arrays.copyOfRange(combined, NONCE_BYTES, combined.length);

            Cipher cipher = Cipher.getInstance(AES_GCM);
            cipher.init(Cipher.DECRYPT_MODE, encKey, new GCMParameterSpec(GCM_TAG_BITS, nonce));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to decrypt email", e);
        }
    }

    /** Trim + lowercase — matches the normalisation already used throughout the auth flow. */
    public String normalise(String email) {
        return email.trim().toLowerCase();
    }

    private static byte[] hmac(byte[] key, byte[] message) {
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(new SecretKeySpec(key, HMAC));
            return mac.doFinal(message);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC derivation failed", e);
        }
    }
}

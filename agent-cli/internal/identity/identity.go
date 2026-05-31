package identity

import (
	"crypto/ed25519"
	"crypto/rand"
	"encoding/base64"
	"fmt"
	"os"
	"path/filepath"
	"time"
)

const keyFile = "identity.key" // 64-byte Ed25519 private key (seed + public)

func keyPath() (string, error) {
	home, err := os.UserHomeDir()
	if err != nil {
		return "", err
	}
	return filepath.Join(home, ".agent-cli", keyFile), nil
}

// GetOrCreate loads the Ed25519 key pair, generating one on first use.
// Private key is stored at ~/.agent-cli/identity.key (mode 0600).
func GetOrCreate() (ed25519.PrivateKey, ed25519.PublicKey, error) {
	path, err := keyPath()
	if err != nil {
		return nil, nil, err
	}

	data, err := os.ReadFile(path)
	if err == nil && len(data) == ed25519.PrivateKeySize {
		priv := ed25519.PrivateKey(data)
		return priv, priv.Public().(ed25519.PublicKey), nil
	}

	// Generate a new key pair
	pub, priv, err := ed25519.GenerateKey(rand.Reader)
	if err != nil {
		return nil, nil, fmt.Errorf("key generation failed: %w", err)
	}
	if err := os.MkdirAll(filepath.Dir(path), 0700); err != nil {
		return nil, nil, err
	}
	if err := os.WriteFile(path, []byte(priv), 0600); err != nil {
		return nil, nil, fmt.Errorf("failed to save key: %w", err)
	}
	return priv, pub, nil
}

// PublicKeyBase64 returns the Base64-encoded raw public key (32 bytes) for
// registering with the server via POST /api/v1/auth/register-key.
func PublicKeyBase64() (string, error) {
	_, pub, err := GetOrCreate()
	if err != nil {
		return "", err
	}
	return base64.StdEncoding.EncodeToString(pub), nil
}

// Sign creates an X-Cli-Signature value for the given request.
//
// Canonical message:
//
//	"{cliVersion} {METHOD} {/api/path} {email} {unixTimestamp}"
//
// Returns (signature, timestamp) to be sent as X-Cli-Signature and
// X-Cli-Timestamp headers.
func Sign(cliVersion, method, path, email string) (sig string, timestamp int64, err error) {
	priv, _, err := GetOrCreate()
	if err != nil {
		return "", 0, err
	}

	timestamp = time.Now().Unix()
	message := fmt.Sprintf("%s %s %s %s %d", cliVersion, method, path, email, timestamp)
	sigBytes := ed25519.Sign(priv, []byte(message))
	return base64.StdEncoding.EncodeToString(sigBytes), timestamp, nil
}

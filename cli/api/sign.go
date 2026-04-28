package api

import (
	"bytes"
	"crypto/ed25519"
	"crypto/sha256"
	"encoding/base64"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"os"
	"path/filepath"
	"strconv"
	"time"

	"cli/config"
)

func loadPrivateKey() (ed25519.PrivateKey, error) {
	home, _ := os.UserHomeDir()
	data, err := os.ReadFile(filepath.Join(home, ".config", "agent-system", "signing_key"))
	if err != nil {
		return nil, fmt.Errorf("signing key not found — run: agent-system keygen")
	}
	raw, err := base64.StdEncoding.DecodeString(string(bytes.TrimSpace(data)))
	if err != nil {
		return nil, fmt.Errorf("invalid signing key format")
	}
	if len(raw) != ed25519.PrivateKeySize {
		return nil, fmt.Errorf("invalid signing key size")
	}
	return ed25519.PrivateKey(raw), nil
}

func signedDo(method, path string, body any) (json.RawMessage, error) {
	keyID := config.KeyID()
	if keyID == "" {
		return nil, fmt.Errorf("key ID not configured — run: agent-system config --key-id <id>")
	}

	priv, err := loadPrivateKey()
	if err != nil {
		return nil, err
	}

	var bodyBytes []byte
	if body != nil {
		bodyBytes, err = json.Marshal(body)
		if err != nil {
			return nil, err
		}
	}

	ts := strconv.FormatInt(time.Now().Unix(), 10)
	hash := sha256.Sum256(bodyBytes)
	bodyHash := hex.EncodeToString(hash[:])
	signedStr := method + "\n" + path + "\n" + ts + "\n" + bodyHash
	sig := ed25519.Sign(priv, []byte(signedStr))

	req, err := http.NewRequest(method, config.URL()+path, bytes.NewReader(bodyBytes))
	if err != nil {
		return nil, err
	}
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("X-Key-ID", keyID)
	req.Header.Set("X-Timestamp", ts)
	req.Header.Set("X-Signature", base64.StdEncoding.EncodeToString(sig))

	resp, err := httpClient.Do(req)
	if err != nil {
		return nil, fmt.Errorf("connection failed: %w", err)
	}
	defer resp.Body.Close()

	b, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, err
	}

	switch resp.StatusCode {
	case 401:
		return nil, fmt.Errorf("signature rejected — check key ID and signing key match keys.json")
	case 204:
		return json.RawMessage("{}"), nil
	}
	if resp.StatusCode >= 400 {
		return nil, fmt.Errorf("HTTP %d: %s", resp.StatusCode, string(b))
	}
	if len(b) == 0 {
		return json.RawMessage("{}"), nil
	}
	return json.RawMessage(b), nil
}

func SignedPost(path string, body any) (json.RawMessage, error)   { return signedDo("POST", path, body) }
func SignedGet(path string) (json.RawMessage, error)              { return signedDo("GET", path, nil) }
func SignedDelete(path string) (json.RawMessage, error)           { return signedDo("DELETE", path, nil) }

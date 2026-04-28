package keys

import (
	"crypto/ed25519"
	"encoding/base64"
	"encoding/json"
	"fmt"
	"os"
)

// KeyInfo holds everything the gateway needs per registered key.
type KeyInfo struct {
	PublicKey ed25519.PublicKey
	Email     string // forwarded as X-Key-Owner to the backend
}

type Store struct {
	path string
}

type entry struct {
	ID        string `json:"id"`
	PublicKey string `json:"public_key"`
	Email     string `json:"email"`
}

type keyFile struct {
	Keys []entry `json:"keys"`
}

func New(path string) *Store {
	return &Store{path: path}
}

// Get looks up key info for the given key ID.
// The file is re-read on every call to support hot-reloading without restart.
func (s *Store) Get(id string) (KeyInfo, error) {
	data, err := os.ReadFile(s.path)
	if err != nil {
		return KeyInfo{}, fmt.Errorf("cannot read keys file %s: %w", s.path, err)
	}
	var kf keyFile
	if err := json.Unmarshal(data, &kf); err != nil {
		return KeyInfo{}, fmt.Errorf("invalid keys file: %w", err)
	}
	for _, k := range kf.Keys {
		if k.ID != id {
			continue
		}
		raw, err := base64.StdEncoding.DecodeString(k.PublicKey)
		if err != nil {
			return KeyInfo{}, fmt.Errorf("invalid base64 for key %s: %w", id, err)
		}
		if len(raw) != ed25519.PublicKeySize {
			return KeyInfo{}, fmt.Errorf("wrong key size for %s: got %d, want %d", id, len(raw), ed25519.PublicKeySize)
		}
		return KeyInfo{PublicKey: ed25519.PublicKey(raw), Email: k.Email}, nil
	}
	return KeyInfo{}, fmt.Errorf("key not found: %s", id)
}

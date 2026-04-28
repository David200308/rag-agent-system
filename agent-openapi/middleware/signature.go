package middleware

import (
	"bytes"
	"context"
	"crypto/ed25519"
	"crypto/sha256"
	"encoding/base64"
	"encoding/hex"
	"fmt"
	"io"
	"net/http"
	"strconv"
	"time"

	"agent-openapi/keys"
)

type ctxKey string

// OwnerKey is the request context key that carries the verified key owner email.
const OwnerKey ctxKey = "keyOwner"

// KeyStore is satisfied by *keys.Store.
type KeyStore interface {
	Get(id string) (keys.KeyInfo, error)
}

// Signature returns middleware that verifies Ed25519 request signatures.
//
// Required headers:
//
//	X-Key-ID    — registered key identifier
//	X-Timestamp — Unix seconds (requests older than ±300s are rejected)
//	X-Signature — base64(ed25519.Sign(privKey, signedString))
//
// Signed string (newline-separated):
//
//	METHOD\nPATH\nTIMESTAMP\nhex(sha256(body))
//
// On success the verified owner email is stored in the request context under OwnerKey.
func Signature(store KeyStore) func(http.Handler) http.Handler {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			keyID := r.Header.Get("X-Key-ID")
			tsStr := r.Header.Get("X-Timestamp")
			sigStr := r.Header.Get("X-Signature")

			if keyID == "" || tsStr == "" || sigStr == "" {
				jsonErr(w, "missing headers: X-Key-ID, X-Timestamp, X-Signature", http.StatusUnauthorized)
				return
			}

			ts, err := strconv.ParseInt(tsStr, 10, 64)
			if err != nil {
				jsonErr(w, "X-Timestamp must be a Unix epoch integer", http.StatusUnauthorized)
				return
			}
			diff := time.Now().Unix() - ts
			if diff < 0 {
				diff = -diff
			}
			if diff > 300 {
				jsonErr(w, "timestamp expired (window: ±300s)", http.StatusUnauthorized)
				return
			}

			info, err := store.Get(keyID)
			if err != nil {
				jsonErr(w, "unknown key id", http.StatusUnauthorized)
				return
			}

			body, err := io.ReadAll(r.Body)
			if err != nil {
				jsonErr(w, "failed to read request body", http.StatusBadRequest)
				return
			}
			r.Body = io.NopCloser(bytes.NewReader(body))

			h := sha256.Sum256(body)
			msg := fmt.Sprintf("%s\n%s\n%s\n%s",
				r.Method, r.URL.Path, tsStr, hex.EncodeToString(h[:]))

			sig, err := base64.StdEncoding.DecodeString(sigStr)
			if err != nil {
				jsonErr(w, "X-Signature must be base64-encoded", http.StatusUnauthorized)
				return
			}

			if !ed25519.Verify(info.PublicKey, []byte(msg), sig) {
				jsonErr(w, "signature verification failed", http.StatusUnauthorized)
				return
			}

			ctx := context.WithValue(r.Context(), OwnerKey, info.Email)
			next.ServeHTTP(w, r.WithContext(ctx))
		})
	}
}

func jsonErr(w http.ResponseWriter, msg string, code int) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(code)
	fmt.Fprintf(w, `{"error":%q}`, msg)
}

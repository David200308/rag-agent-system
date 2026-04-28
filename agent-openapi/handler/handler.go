package handler

import (
	"bytes"
	"io"
	"net/http"
	"time"

	"agent-openapi/middleware"
)

type Handler struct {
	backendURL  string
	internalKey string
	client      *http.Client
}

func New(backendURL, internalKey string) *Handler {
	return &Handler{
		backendURL:  backendURL,
		internalKey: internalKey,
		client:      &http.Client{Timeout: 60 * time.Second},
	}
}

func (h *Handler) proxy(w http.ResponseWriter, r *http.Request, backendPath string) {
	body, err := io.ReadAll(r.Body)
	if err != nil {
		http.Error(w, `{"error":"failed to read body"}`, http.StatusBadRequest)
		return
	}

	req, err := http.NewRequest(r.Method, h.backendURL+backendPath, bytes.NewReader(body))
	if err != nil {
		http.Error(w, `{"error":"internal error"}`, http.StatusInternalServerError)
		return
	}
	req.Header.Set("Content-Type", "application/json")

	// Authenticate with the backend using the shared internal key (never expires).
	// The backend's AuthFilter validates X-Gateway-Key and trusts X-Key-Owner
	// as the authenticated user identity — no JWT needed.
	req.Header.Set("X-Gateway-Key", h.internalKey)
	if owner, ok := r.Context().Value(middleware.OwnerKey).(string); ok && owner != "" {
		req.Header.Set("X-Key-Owner", owner)
	}

	resp, err := h.client.Do(req)
	if err != nil {
		http.Error(w, `{"error":"backend unavailable"}`, http.StatusBadGateway)
		return
	}
	defer resp.Body.Close()

	if ct := resp.Header.Get("Content-Type"); ct != "" {
		w.Header().Set("Content-Type", ct)
	}
	w.WriteHeader(resp.StatusCode)
	io.Copy(w, resp.Body) //nolint:errcheck
}

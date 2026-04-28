package handler

import "net/http"

// RequestOtp proxies POST /v1/auth/request-otp → /api/v1/auth/request-otp (public, no signature).
func (h *Handler) RequestOtp(w http.ResponseWriter, r *http.Request) {
	h.proxyPublic(w, r, "/api/v1/auth/request-otp")
}

// VerifyOtp proxies POST /v1/auth/verify-otp → /api/v1/auth/verify-otp (public, no signature).
func (h *Handler) VerifyOtp(w http.ResponseWriter, r *http.Request) {
	h.proxyPublic(w, r, "/api/v1/auth/verify-otp")
}

package handler

import "net/http"

// AgentQuery proxies POST /v1/agent/query → POST /api/v1/agent/query
func (h *Handler) AgentQuery(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Error(w, `{"error":"method not allowed"}`, http.StatusMethodNotAllowed)
		return
	}
	h.proxy(w, r, "/api/v1/agent/query")
}

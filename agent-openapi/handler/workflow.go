package handler

import "net/http"

// WorkflowList proxies GET /v1/workflow → GET /api/v1/workflow
func (h *Handler) WorkflowList(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		http.Error(w, `{"error":"method not allowed"}`, http.StatusMethodNotAllowed)
		return
	}
	h.proxy(w, r, "/api/v1/workflow")
}

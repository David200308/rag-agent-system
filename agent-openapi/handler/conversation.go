package handler

import "net/http"

// ConversationList proxies GET /v1/conversations → /api/v1/agent/conversations
func (h *Handler) ConversationList(w http.ResponseWriter, r *http.Request) {
	h.proxy(w, r, "/api/v1/agent/conversations")
}

// ConversationGet proxies GET /v1/conversations/{id} → /api/v1/agent/conversations/{id}
func (h *Handler) ConversationGet(w http.ResponseWriter, r *http.Request) {
	h.proxy(w, r, "/api/v1/agent/conversations/"+r.PathValue("id"))
}

// ConversationDelete proxies DELETE /v1/conversations/{id} → /api/v1/agent/conversations/{id}
func (h *Handler) ConversationDelete(w http.ResponseWriter, r *http.Request) {
	h.proxy(w, r, "/api/v1/agent/conversations/"+r.PathValue("id"))
}

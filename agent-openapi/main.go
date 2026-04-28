package main

import (
	"log"
	"net/http"

	"agent-openapi/config"
	"agent-openapi/handler"
	"agent-openapi/keys"
	"agent-openapi/middleware"
)

func main() {
	cfg := config.Load()

	if cfg.InternalKey == "" {
		log.Fatal("[agent-openapi] INTERNAL_KEY must be set — generate one with: openssl rand -hex 32")
	}

	store := keys.New(cfg.KeysFile)
	h := handler.New(cfg.BackendURL, cfg.InternalKey)
	sig := middleware.Signature(store)

	mux := http.NewServeMux()

	// Public routes — no signature required
	mux.HandleFunc("POST /v1/auth/request-otp", h.RequestOtp)
	mux.HandleFunc("POST /v1/auth/verify-otp", h.VerifyOtp)
	mux.HandleFunc("GET /health", func(w http.ResponseWriter, _ *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		w.Write([]byte(`{"status":"ok"}`)) //nolint:errcheck
	})

	// Authenticated routes — Ed25519 signature required
	mux.Handle("POST /v1/agent/query", sig(http.HandlerFunc(h.AgentQuery)))
	mux.Handle("GET /v1/conversations", sig(http.HandlerFunc(h.ConversationList)))
	mux.Handle("GET /v1/conversations/{id}", sig(http.HandlerFunc(h.ConversationGet)))
	mux.Handle("DELETE /v1/conversations/{id}", sig(http.HandlerFunc(h.ConversationDelete)))
	mux.Handle("GET /v1/workflow", sig(http.HandlerFunc(h.WorkflowList)))
	mux.Handle("GET /v1/workflow/{id}", sig(http.HandlerFunc(h.WorkflowGet)))
	mux.Handle("GET /v1/workflow/{id}/agents", sig(http.HandlerFunc(h.WorkflowAgents)))
	mux.Handle("POST /v1/workflow/{id}/runs", sig(http.HandlerFunc(h.WorkflowRunTrigger)))
	mux.Handle("GET /v1/workflow/{id}/runs", sig(http.HandlerFunc(h.WorkflowRuns)))
	mux.Handle("GET /v1/workflow/runs/{id}/logs", sig(http.HandlerFunc(h.WorkflowRunLogs)))

	log.Printf("[agent-openapi] listening on :%s", cfg.Port)
	log.Printf("[agent-openapi] backend: %s", cfg.BackendURL)
	log.Printf("[agent-openapi] keys:    %s", cfg.KeysFile)

	if err := http.ListenAndServe(":"+cfg.Port, mux); err != nil {
		log.Fatalf("[agent-openapi] %v", err)
	}
}

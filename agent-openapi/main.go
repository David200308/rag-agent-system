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
	mux.Handle("POST /v1/agent/query", sig(http.HandlerFunc(h.AgentQuery)))
	mux.Handle("GET /v1/workflow", sig(http.HandlerFunc(h.WorkflowList)))
	mux.HandleFunc("GET /health", func(w http.ResponseWriter, _ *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		w.Write([]byte(`{"status":"ok"}`)) //nolint:errcheck
	})

	log.Printf("[agent-openapi] listening on :%s", cfg.Port)
	log.Printf("[agent-openapi] backend: %s", cfg.BackendURL)
	log.Printf("[agent-openapi] keys:    %s", cfg.KeysFile)

	if err := http.ListenAndServe(":"+cfg.Port, mux); err != nil {
		log.Fatalf("[agent-openapi] %v", err)
	}
}

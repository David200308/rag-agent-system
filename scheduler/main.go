package main

import (
	"log"
	"net/http"
	"scheduler/config"
	"scheduler/cron"
	"scheduler/handler"
	"scheduler/store"
)

func main() {
	cfg := config.Load()
	st := store.Connect(cfg)
	runner := cron.NewRunner(cfg, st)
	runner.LoadAndStart()

	h := handler.New(cfg, st, runner)

	mux := http.NewServeMux()
	mux.HandleFunc("GET /health", h.Health)
	mux.HandleFunc("GET /schedules", h.List)
	mux.HandleFunc("POST /schedules", h.Create)
	mux.HandleFunc("PATCH /schedules/{id}", h.Update)
	mux.HandleFunc("DELETE /schedules/{id}", h.Delete)

	log.Printf("[scheduler] listening on :%s", cfg.Port)
	if err := http.ListenAndServe(":"+cfg.Port, mux); err != nil {
		log.Fatalf("[scheduler] server error: %v", err)
	}
}

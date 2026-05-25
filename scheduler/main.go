package main

import (
	"log"
	"net/http"

	"go.temporal.io/sdk/client"
	"go.temporal.io/sdk/worker"

	"scheduler/activity"
	"scheduler/config"
	"scheduler/handler"
	ragworkflow "scheduler/workflow"
)

func main() {
	cfg := config.Load()

	// Connect to Temporal server
	tc, err := client.Dial(client.Options{
		HostPort:  cfg.TemporalHostPort,
		Namespace: cfg.TemporalNamespace,
	})
	if err != nil {
		log.Fatalf("[scheduler] cannot connect to Temporal at %s: %v", cfg.TemporalHostPort, err)
	}
	defer tc.Close()

	// Start worker: polls the task queue and executes workflows + activities
	w := worker.New(tc, ragworkflow.TaskQueue, worker.Options{})
	w.RegisterWorkflow(ragworkflow.RagQueryWorkflow)
	w.RegisterActivity(activity.TriggerActivity)
	if err := w.Start(); err != nil {
		log.Fatalf("[scheduler] cannot start worker: %v", err)
	}
	defer w.Stop()
	log.Printf("[scheduler] worker started on task-queue=%s", ragworkflow.TaskQueue)

	// HTTP REST API
	h := handler.New(cfg, tc)
	mux := http.NewServeMux()
	mux.HandleFunc("GET /health", h.Health)
	mux.HandleFunc("GET /schedules", h.List)
	mux.HandleFunc("POST /schedules", h.Create)
	mux.HandleFunc("PATCH /schedules/{id}", h.Update)
	mux.HandleFunc("DELETE /schedules/{id}", h.Delete)
	mux.HandleFunc("GET /schedules/{id}/runs", h.ListRuns)

	// Internal endpoints — service-key auth, used by Spring Boot workflow engine
	mux.HandleFunc("POST /internal/schedules", h.InternalCreate)
	mux.HandleFunc("GET /internal/schedules", h.InternalList)
	mux.HandleFunc("DELETE /internal/schedules/{id}", h.InternalDelete)

	log.Printf("[scheduler] listening on :%s", cfg.Port)
	if err := http.ListenAndServe(":"+cfg.Port, mux); err != nil {
		log.Fatalf("[scheduler] server error: %v", err)
	}
}
